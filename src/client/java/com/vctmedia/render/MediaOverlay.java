package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.vctmedia.mixin.client.DrawContextAccessor;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MediaOverlay {
    private static final float REFERENCE_HEIGHT = 1080.0f;
    private static boolean debugPrinted = false; // Solo imprime los logs una vez para no ahogar la consola

    private static class RenderCache {
        Identifier id;
        NativeImageBackedTexture backingTexture;
        int glId = -1;
        int fboId = -1;
        long lastCopyMs = 0;
        int lastWidth = -1;
        int lastHeight = -1;

        void free() {
            if (id != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
                id = null;
            }
            if (backingTexture != null) {
                backingTexture.close();
                backingTexture = null;
            }
            if (fboId != -1) {
                GL30.glDeleteFramebuffers(fboId);
                fboId = -1;
            }
        }
    }

    private static final Map<AbstractMedia, RenderCache> RENDER_CACHES = new HashMap<>();

    // Extrae el GL ID real de la textura manejada por Minecraft
    private static int findGlId(Object target) {
        if (target == null) return -1;
        try {
            Object gpuTexture = null;
            Class<?> clazz = target.getClass();

            // Buscar el GpuTexture dentro de AbstractTexture
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.getName().equals("glTexture") || field.getType().getSimpleName().contains("GpuTexture")) {
                        field.setAccessible(true);
                        gpuTexture = field.get(target);
                        break;
                    }
                }
                if (gpuTexture != null) break;
                clazz = clazz.getSuperclass();
            }

            if (gpuTexture == null) gpuTexture = target;

            // Extraer el int (ID) del GpuTexture
            clazz = gpuTexture.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        String name = field.getName().toLowerCase();
                        if (name.contains("id") || name.contains("texture")) {
                            field.setAccessible(true);
                            int id = (int) field.get(gpuTexture);
                            if (id > 0) return id;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    // Extrae la View de la textura
    private static GpuTextureView getTextureView(NativeImageBackedTexture tex) {
        try {
            for (Field field : tex.getClass().getSuperclass().getDeclaredFields()) {
                if (field.getType() == GpuTextureView.class) {
                    field.setAccessible(true);
                    return (GpuTextureView) field.get(tex);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Extrae el Sampler de la textura
    private static GpuSampler getSampler(NativeImageBackedTexture tex) {
        try {
            for (Field field : tex.getClass().getSuperclass().getDeclaredFields()) {
                if (field.getType() == GpuSampler.class) {
                    field.setAccessible(true);
                    return (GpuSampler) field.get(tex);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void uploadFrameGPU(RenderCache cache, int srcGlId, int width, int height) {
        if (cache.backingTexture == null || cache.lastWidth != width || cache.lastHeight != height) {
            if (cache.backingTexture != null) {
                cache.free();
            }

            // Creamos una imagen vacía y su textura
            NativeImage img = new NativeImage(width, height, false);
            cache.backingTexture = new NativeImageBackedTexture(() -> "vctmedia", img);

            // LA CLAVE: Registrarla para que Minecraft le asigne memoria en la VRAM
            cache.id = Identifier.of("vctmedia", "frame_" + System.nanoTime());
            MinecraftClient.getInstance().getTextureManager().registerTexture(cache.id, cache.backingTexture);

            cache.lastWidth = width;
            cache.lastHeight = height;
            cache.glId = -1;

            if (!debugPrinted) System.out.println("[ViciontMedia] Textura creada y registrada correctamente.");
        }

        if (cache.fboId == -1) {
            cache.fboId = GL30.glGenFramebuffers();
            if (!debugPrinted) System.out.println("[ViciontMedia] FBO generado: " + cache.fboId);
        }

        if (cache.glId <= 0) {
            cache.glId = findGlId(cache.backingTexture);
            if (cache.glId <= 0) {
                if (!debugPrinted) System.out.println("[ViciontMedia] ERROR FATAL: findGlId devolvió -1. La textura nunca se copiará a la gráfica.");
                return;
            }
            if (!debugPrinted) System.out.println("[ViciontMedia] GL ID obtenido correctamente: " + cache.glId);
        }

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            // Copiamos el frame de WaterMedia a nuestra textura legal de Minecraft
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, cache.fboId);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcGlId, 0);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cache.glId);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

            if (!debugPrinted) {
                System.out.println("[ViciontMedia] Frame copiado con éxito de WaterMedia (ID " + srcGlId + ") a Minecraft (ID " + cache.glId + ").");
            }
        } catch (Exception e) {
            System.out.println("[ViciontMedia] EXCEPCIÓN en FBO copy:");
            e.printStackTrace();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        }
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        var medias = MediaOrchestrator.getActiveList();

        Iterator<Map.Entry<AbstractMedia, RenderCache>> it = RENDER_CACHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<AbstractMedia, RenderCache> entry = it.next();
            if (!medias.contains(entry.getKey())) {
                entry.getValue().free();
                it.remove();
            }
        }

        if (!medias.isEmpty()) {
            Window window = MinecraftClient.getInstance().getWindow();

            float guiScale = (float) window.getScaleFactor();
            int screenWidthPx = window.getFramebufferWidth();
            int screenHeightPx = window.getFramebufferHeight();
            long now = System.currentTimeMillis();

            for (AbstractMedia media : medias) {
                int textureId = media.getGlId(tickCounter.getTickProgress(true));
                if (textureId <= 0) continue;

                int mediaWidth = media.getWidth();
                int mediaHeight = media.getHeight();
                if (mediaWidth <= 0 || mediaHeight <= 0) continue;

                RenderCache cache = RENDER_CACHES.computeIfAbsent(media, k -> new RenderCache());

                if (now - cache.lastCopyMs >= 33 || cache.backingTexture == null || cache.lastWidth != mediaWidth || cache.lastHeight != mediaHeight) {
                    uploadFrameGPU(cache, textureId, mediaWidth, mediaHeight);
                    cache.lastCopyMs = now;
                }

                if (cache.backingTexture == null) continue;

                float alpha = media.opacity / 100.0f;
                // Aseguramos que el Alpha es 100% opaco para que no se haga invisible
                int color = ((int)(alpha * 255.0f) & 0xFF) << 24 | 0xFFFFFF;

                context.getMatrices().pushMatrix();

                // Usamos siempre 3 floats para evitar el error de Matrix3x2f
                context.getMatrices().scale(1.0f / guiScale, 1.0f / guiScale);

                float renderScale = screenHeightPx / REFERENCE_HEIGHT;
                context.getMatrices().scale(renderScale, renderScale);

                float virtualScreenWidth = screenWidthPx / renderScale;
                float virtualScreenHeight = REFERENCE_HEIGHT;

                float x = 0, y = 0;
                float width, height;

                if (media.size <= 0) {
                    width = virtualScreenWidth;
                    height = virtualScreenHeight;
                } else {
                    width = media.size;
                    height = width * ((float) mediaHeight / mediaWidth);

                    String pos = media.pos != null ? media.pos.toLowerCase() : "center";

                    switch (pos) {
                        case "topleft": x = 0; y = 0; break;
                        case "top": case "topcenter": x = (virtualScreenWidth - width) / 2.0f; y = 0; break;
                        case "topright": x = virtualScreenWidth - width; y = 0; break;
                        case "left": case "centerleft": x = 0; y = (virtualScreenHeight - height) / 2.0f; break;
                        case "center": x = (virtualScreenWidth - width) / 2.0f; y = (virtualScreenHeight - height) / 2.0f; break;
                        case "right": case "centerright": x = virtualScreenWidth - width; y = (virtualScreenHeight - height) / 2.0f; break;
                        case "bottomleft": x = 0; y = virtualScreenHeight - height; break;
                        case "bottom": case "bottomcenter": x = (virtualScreenWidth - width) / 2.0f; y = virtualScreenHeight - height; break;
                        case "bottomright": x = virtualScreenWidth - width; y = virtualScreenHeight - height; break;
                        default:
                            if (pos.contains(",")) {
                                try {
                                    String[] parts = pos.split(",");
                                    float percentX = Float.parseFloat(parts[0].trim());
                                    float percentY = Float.parseFloat(parts[1].trim());
                                    float basePx = virtualScreenWidth * (percentX / 100.0f);
                                    float basePy = virtualScreenHeight * (percentY / 100.0f);
                                    x = basePx - (width / 2.0f);
                                    y = basePy - (height / 2.0f);
                                } catch (NumberFormatException ignored) {}
                            }
                            break;
                    }
                }

                GpuTextureView textureView = getTextureView(cache.backingTexture);
                GpuSampler gpuSampler = getSampler(cache.backingTexture);

                if (textureView != null && gpuSampler != null) {
                    RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

                    // Dibujamos usando tu Mixin (Invirtiendo v1=1f y v2=0f)
                    ((DrawContextAccessor) context).vctmedia$drawTexturedQuad(
                            pipeline, textureView, gpuSampler,
                            (int) x, (int) y, (int) (x + width), (int) (y + height),
                            0f, 1f, 1f, 0f, color);

                    if (!debugPrinted) {
                        System.out.println("[ViciontMedia] Render finalizado en pantalla con éxito.");
                        debugPrinted = true;
                    }
                } else {
                    if (!debugPrinted) {
                        System.out.println("[ViciontMedia] ERROR GRAVE: Reflection falló. textureView=" + (textureView != null) + ", gpuSampler=" + (gpuSampler != null));
                        debugPrinted = true;
                    }
                }

                context.getMatrices().popMatrix();
            }
        }

        VolumeManager.render(context);
    }

    public static void close() {
        for (RenderCache cache : RENDER_CACHES.values()) {
            cache.free();
        }
        RENDER_CACHES.clear();
    }
}