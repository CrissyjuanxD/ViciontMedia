package com.vctmedia.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.vctmedia.mixin.client.PostEffectPassAccessor;
import com.vctmedia.mixin.client.PostEffectProcessorAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShaderManager {

    public static final List<String> SHADERS = Arrays.asList(
            "none", "antialias", "art", "bits", "blobs", "blobs2", "blur", "bumpy",
            "color_convolve", "creeper", "deconverge", "desaturate", "flip",
            "green", "invert", "notch", "ntsc", "outline", "pencil", "phosphor",
            "scan_pincushion", "sobel", "spider", "wobble", "wobbleslow", "blood",
            "nightv", "wobblelava", "confusion", "anim_sobel"
    );

    public static PostEffectProcessor currentShader;
    public static boolean isEnabled = false;
    public static LinkedList<String> shaderStack = new LinkedList<>();

    public static void loadShader(String name) {
        if (name == null || name.equalsIgnoreCase("none") || name.equalsIgnoreCase("off") || name.equalsIgnoreCase("normal")) {
            removeShader("none");
            return;
        }

        shaderStack.remove(name);
        shaderStack.addLast(name);
        applyTopShader();
    }

    public static void removeShader(String name) {
        if (name != null && !name.equalsIgnoreCase("none")) {
            shaderStack.remove(name);
        } else {
            shaderStack.clear();
        }

        isEnabled = false;
        if (currentShader != null) {
            currentShader.close();
            currentShader = null;
        }
        if (!shaderStack.isEmpty()) {
            applyTopShader();
        }
    }

    private static void applyTopShader() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {

            if (shaderStack.isEmpty()) {
                return;
            }

            String topShader = shaderStack.getLast();
            Identifier shaderId = Identifier.of("minecraft", "shaders/post/" + topShader.toLowerCase() + ".json");

            try {
                if (currentShader != null) {
                    currentShader.close();
                    currentShader = null;
                }

                ShaderLoader shaderLoader = client.getShaderLoader();
                Set<Identifier> externalTargets = new HashSet<>();
                externalTargets.add(PostEffectProcessor.MAIN);
                currentShader = shaderLoader.loadPostEffect(shaderId, externalTargets);

                isEnabled = true;

            } catch (Exception e) {
                System.err.println("No se pudo cargar el shader: " + topShader);
                e.printStackTrace();
                shaderStack.removeLast();
                applyTopShader();
            }
        });
    }

    public static void updateFadeAnim() {
        if (!isEnabled || currentShader == null) return;

        float fadeAlpha = FadeManager.getFadeAlpha();
        if (fadeAlpha <= 0.0f) return;

        PostEffectProcessorAccessor processorAccessor = (PostEffectProcessorAccessor) (Object) currentShader;
        List<PostEffectPass> passes = processorAccessor.getPasses();

        for (PostEffectPass pass : passes) {
            PostEffectPassAccessor passAccessor = (PostEffectPassAccessor) (Object) pass;
            String passId = passAccessor.getId();

            if ("anim_sobel".equals(passId)) {
                Map<String, GpuBuffer> uniformBuffers = passAccessor.getUniformBuffers();
                GpuBuffer oldBuffer = uniformBuffers.get("Fade");
                if (oldBuffer != null) {
                    oldBuffer.close();
                }

                ByteBuffer data = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
                data.putFloat(fadeAlpha);
                data.flip();

                GpuBuffer newBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "vctmedia_fade_uniform",
                        GpuBuffer.USAGE_UNIFORM,
                        data
                );
                uniformBuffers.put("Fade", newBuffer);
                break;
            }
        }
    }
}
