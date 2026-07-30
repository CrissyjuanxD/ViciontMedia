package com.vctmedia.util;

import com.vctmedia.mixin.client.PostEffectProcessorAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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

    // -- VARIABLES PARA LA ANIMACIÓN (FADE) --
    public static float fadeAmount = 0.0f;
    public static boolean isFadingOut = false;
    public static boolean hasFadeUniform = false; // Nuevo: Detecta si el shader soporta la animación
    public static final float FADE_SPEED = 0.03f;

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

        // Si el shader actual soporta Fade, iniciamos la salida suave
        if (hasFadeUniform) {
            isFadingOut = true;
        } else {
            // Si es un shader normal, lo apagamos de golpe para que no se quede pegado
            isEnabled = false;
            if (currentShader != null) {
                currentShader.close();
                currentShader = null;
            }
            if (!shaderStack.isEmpty()) {
                applyTopShader();
            }
        }
    }

    private static void applyTopShader() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {

            if (shaderStack.isEmpty()) {
                if (hasFadeUniform) {
                    isFadingOut = true;
                }
                return;
            }

            String topShader = shaderStack.getLast();
            Identifier shaderId = Identifier.of("minecraft", "shaders/post/" + topShader.toLowerCase() + ".json");

            try {
                if (currentShader != null) {
                    currentShader.close();
                    currentShader = null;
                }

                currentShader = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), client.getFramebuffer(), shaderId);
                currentShader.setupDimensions(client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());

                // --- DETECTOR AUTOMÁTICO DE FADE ---
                hasFadeUniform = false;
                List<PostEffectPass> passes = ((PostEffectProcessorAccessor) currentShader).getPasses();
                for (PostEffectPass pass : passes) {
                    if (pass.getProgram().getUniformByName("Fade") != null) {
                        hasFadeUniform = true;
                        break;
                    }
                }

                isEnabled = true;
                isFadingOut = false;
                // Si tiene animación, empieza en 0. Si no, lo forzamos al máximo directamente
                fadeAmount = hasFadeUniform ? 0.0f : 1.0f;

            } catch (Exception e) {
                System.err.println("No se pudo cargar el shader: " + topShader);
                shaderStack.removeLast();
                applyTopShader();
            }
        });
    }

    public static void updateFadeAnim() {
        // Solo animamos si el shader está activo y realmente soporta el Fade
        if (!isEnabled || currentShader == null || !hasFadeUniform) return;

        if (isFadingOut) {
            fadeAmount -= FADE_SPEED;
            if (fadeAmount <= 0.0f) {
                fadeAmount = 0.0f;
                isEnabled = false;
                currentShader.close();
                currentShader = null;
                isFadingOut = false;

                if (!shaderStack.isEmpty()) {
                    applyTopShader();
                }
                return;
            }
        } else {
            fadeAmount += FADE_SPEED;
            if (fadeAmount > 1.0f) {
                fadeAmount = 1.0f;
            }
        }

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) currentShader).getPasses();
        for (PostEffectPass pass : passes) {
            GlUniform fadeUniform = pass.getProgram().getUniformByName("Fade");
            if (fadeUniform != null) {
                fadeUniform.set(fadeAmount);
            }
        }
    }
}