package com.vctmedia.util;

import com.vctmedia.ViciontMedia;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ShaderManager {

    public static final List<String> SHADERS = Arrays.asList(
            "none",
            "antialias", "art", "bits", "blobs", "blobs2", "blur", "bumpy",
            "color_convolve", "creeper", "deconverge", "desaturate", "flip",
            "green", "invert", "notch", "ntsc", "outline", "pencil", "phosphor",
            "scan_pincushion", "sobel", "spider", "wobble", "wobbleslow",
            "blood", "nightv", "wobblelava", "confusion", "anim_sobel"
    );

    private static final Set<String> CUSTOM_SHADERS = Set.of(
            "blood", "nightv", "wobblelava", "confusion", "anim_sobel"
    );

    @Nullable
    private static PostEffectProcessor currentShader;
    private static boolean isEnabled = false;
    private static int shaderIndex = 0;

    private static long shaderFadeStartMs = 0;
    private static final long SHADER_FADE_DURATION = 800;

    @Nullable
    private static Framebuffer swapBuffer;

    public static void cycleShader() {
        shaderIndex = (shaderIndex + 1) % SHADERS.size();
        loadShader(SHADERS.get(shaderIndex));
    }

    public static void toggleShader() {
        String name = SHADERS.get(shaderIndex);
        if (isEnabled) {
            removeShader(name);
        } else {
            if (name.equals("none")) {
                shaderIndex = 1;
                name = SHADERS.get(shaderIndex);
            }
            loadShader(name);
        }
    }

    public static void loadShader(String name) {
        if (name == null || name.equalsIgnoreCase("none") || name.equalsIgnoreCase("off")) {
            removeShader(name);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                Identifier shaderId = getShaderIdentifier(name);
                PostEffectProcessor effect = client.getShaderLoader()
                        .loadPostEffect(shaderId, DefaultFramebufferSet.MAIN_ONLY);

                if (effect == null) {
                    System.err.println("[ViciontMedia] No se pudo cargar el shader: " + name);
                    isEnabled = false;
                    return;
                }

                if (currentShader != null) {
                    currentShader.close();
                    currentShader = null;
                }

                currentShader = effect;
                isEnabled = true;
                shaderFadeStartMs = System.currentTimeMillis();

            } catch (Exception e) {
                System.err.println("[ViciontMedia] Error al cargar el shader '" + name + "': " + e.getMessage());
                e.printStackTrace();
                isEnabled = false;
            }
        });
    }

    public static void removeShader(String name) {
        isEnabled = false;
        shaderFadeStartMs = 0;
        if (currentShader != null) {
            try { currentShader.close(); } catch (Exception ignored) {}
            currentShader = null;
        }
    }

    private static Identifier getShaderIdentifier(String name) {
        if (CUSTOM_SHADERS.contains(name)) {
            return ViciontMedia.id(name);
        }
        return Identifier.of("minecraft", name);
    }

    public static void render(GameRenderer renderer) {
        if (!isEnabled || currentShader == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer mainBuffer = client.getFramebuffer();
        int width = mainBuffer.textureWidth;
        int height = mainBuffer.textureHeight;

        if (swapBuffer == null
                || swapBuffer.textureWidth != width
                || swapBuffer.textureHeight != height) {
            if (swapBuffer != null) {
                swapBuffer.delete();
            }
            swapBuffer = new SimpleFramebuffer("swap", width, height, true);
        }

        FrameGraphBuilder builder = new FrameGraphBuilder();

        PostEffectProcessor.FramebufferSet framebufferSet = new PostEffectProcessor.FramebufferSet() {
            private final Map<Identifier, Handle<Framebuffer>> map = new HashMap<>();

            {
                Handle<Framebuffer> main = builder.createObjectNode("main", mainBuffer);
                map.put(PostEffectProcessor.MAIN, main);

                Handle<Framebuffer> swap = builder.createObjectNode("swap", swapBuffer);
                map.put(ViciontMedia.id("swap"), swap);
                map.put(Identifier.of("minecraft", "swap"), swap);
            }

            @Override
            public void set(Identifier id, Handle<Framebuffer> framebuffer) {
                map.put(id, framebuffer);
            }

            @Override
            public @Nullable Handle<Framebuffer> get(Identifier id) {
                return map.get(id);
            }
        };

        try {
            currentShader.render(builder, width, height, framebufferSet);

            if (renderer instanceof GameRendererPoolAccessor accessor) {
                ObjectAllocator allocator = accessor.getPool();
                builder.run(allocator);
            }
        } catch (Exception e) {
            System.err.println("[ViciontMedia] Error renderizando shader, desactivando: " + e.getMessage());
            isEnabled = false;
            shaderFadeStartMs = 0;
            if (currentShader != null) {
                try { currentShader.close(); } catch (Exception ignored) {}
                currentShader = null;
            }
        }
    }

    public static float getFadeAlpha() {
        if (!isEnabled || shaderFadeStartMs == 0) {
            return 0.0f;
        }
        long elapsed = System.currentTimeMillis() - shaderFadeStartMs;
        if (elapsed >= SHADER_FADE_DURATION) {
            return 0.0f;
        }
        return 1.0f - ((float) elapsed / SHADER_FADE_DURATION);
    }

    public static void onResize() {
        if (swapBuffer != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            swapBuffer.delete();
            swapBuffer = new SimpleFramebuffer("swap", width, height, true);
        }
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static String getCurrentShaderName() {
        if (shaderIndex < 0 || shaderIndex >= SHADERS.size()) return "none";
        return SHADERS.get(shaderIndex);
    }
}
