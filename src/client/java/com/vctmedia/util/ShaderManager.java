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

/**
 * Shader manager for Minecraft 1.21.11 (yarn build.6).
 *
 * Key API changes from 1.21.1:
 * - PostEffectProcessor loaded via client.getShaderLoader().loadPostEffect(id, DefaultFramebufferSet.MAIN_ONLY)
 * - render() takes (FrameGraphBuilder, int, int, FramebufferSet) instead of (float tickDelta)
 * - FrameGraphBuilder.run() takes ObjectAllocator (ObjectPool implements ObjectAllocator)
 *   — ObjectAllocator is in net.minecraft.client.util.memory, not net.minecraft.client.util
 * - setupDimensions() removed
 * - PostEffectPass no longer has getProgram() — uniforms are GPU-buffer-backed, no GlUniform.set()
 * - Framebuffer.close() replaced by Framebuffer.delete()
 * - Fade animation must be done shader-side (using GameTime from Globals uniform block)
 */
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
                    System.err.println("No se pudo cargar el shader: " + name);
                    return;
                }

                if (currentShader != null) {
                    currentShader.close();
                    currentShader = null;
                }

                currentShader = effect;
                isEnabled = true;

            } catch (Exception e) {
                System.err.println("Error al cargar el shader '" + name + "': " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public static void removeShader(String name) {
        isEnabled = false;
        if (currentShader != null) {
            currentShader.close();
            currentShader = null;
        }
    }

    private static Identifier getShaderIdentifier(String name) {
        if (CUSTOM_SHADERS.contains(name)) {
            return ViciontMedia.id(name);
        }
        return Identifier.of("minecraft", name);
    }

    /**
     * Called every frame from GameRendererMixin to render the active post effect.
     * Uses the same pattern as HazelTheWitch/impact-frames:
     * FrameGraphBuilder + FramebufferSet + builder.run(allocator).
     */
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
            System.err.println("Error renderizando shader: " + e.getMessage());
            e.printStackTrace();
        }
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
