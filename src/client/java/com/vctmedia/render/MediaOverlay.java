package com.vctmedia.render;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.Window;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MediaOverlay {
    private static final float REFERENCE_HEIGHT = 1080.0f;

    private static class RenderCache {
        DynamicTexture backingTexture;
        int glId = -1;
        int fboId = -1;
        long lastCopyMs = 0;
        int lastWidth = -1;
        int lastHeight = -1;

        void free() {
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

    private static int findGlId(Object target) {
        if (target == null) return -1;
        Class<?> currentClass = target.getClass();

        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (method.getReturnType() == int.class) {
                    String name = method.getName().toLowerCase();
                    if (name.equals("getglid") || name.equals("getid") || name.equals("glid")) {
                        try {
                            method.setAccessible(true);
                            int id = (int) method.invoke(target);
                            if (id > 0) return id;
                        } catch (Exception ignored) {}
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        currentClass = target.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    String name = field.getName().toLowerCase();
                    if (name.contains("id") || name.contains("texture")) {
                        try {
                            field.setAccessible(true);
                            int id = (int) field.get(target);
                            if (id > 0) return id;
                        } catch (Exception ignored) {}
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return -1;
    }

    private static void uploadFrameGPU(RenderCache cache, int srcGlId, int width, int height) {
        if (cache.backingTexture == null || cache.lastWidth != width || cache.lastHeight != height) {
            if (cache.backingTexture != null) {
                cache.backingTexture.close();
            }
            cache.backingTexture = new DynamicTexture("vctmedia", width, height, false);
            cache.lastWidth = width;
            cache.lastHeight = height;
            cache.glId = -1;
        }

        if (cache.fboId == -1) {
            cache.fboId = GL30.glGenFramebuffers();
        }

        if (cache.glId <= 0) {
            cache.glId = findGlId(cache.backingTexture.getTextureView());
            if (cache.glId <= 0) {
                cache.glId = findGlId(cache.backingTexture);
            }
            if (cache.glId <= 0) return;
        }

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, cache.fboId);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcGlId, 0);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cache.glId);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
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
            float partialTick = tickCounter.getGameTimeDeltaPartialTick(true);
            Window window = Minecraft.getInstance().getWindow();

            float guiScale = (float) window.getGuiScale();
            int screenWidthPx = window.getWidth();
            int screenHeightPx = window.getHeight();

            long now = System.currentTimeMillis();

            for (AbstractMedia media : medias) {
                int textureId = media.getGlId(partialTick);
                if (textureId <= 0) continue;

                int mediaWidth = media.getWidth();
                int mediaHeight = media.getHeight();
                if (mediaWidth <= 0 || mediaHeight <= 0) continue;

                RenderCache cache = RENDER_CACHES.computeIfAbsent(media, k -> new RenderCache());

                // Límite de 30 FPS (~33ms) INDEPENDIENTE para cada video/gif
                // Esto es la magia para que las Intel HD no mueran intentando copiar frames.
                if (now - cache.lastCopyMs >= 33 || cache.backingTexture == null || cache.lastWidth != mediaWidth || cache.lastHeight != mediaHeight) {
                    uploadFrameGPU(cache, textureId, mediaWidth, mediaHeight);
                    cache.lastCopyMs = now;
                }

                if (cache.backingTexture == null) continue;

                context.pose().pushMatrix();
                context.pose().scale(1.0f / guiScale, 1.0f / guiScale);

                float renderScale = screenHeightPx / REFERENCE_HEIGHT;
                context.pose().scale(renderScale, renderScale);

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

                GpuTextureView view = cache.backingTexture.getTextureView();
                GpuSampler sampler = cache.backingTexture.getSampler();

                int x0 = (int) x;
                int y0 = (int) y;
                int x1 = (int) (x + width);
                int y1 = (int) (y + height);

                context.blit(view, sampler, x0, y0, x1, y1, 0f, 1f, 0f, 1f);

                context.pose().popMatrix();
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