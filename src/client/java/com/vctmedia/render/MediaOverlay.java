package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MediaOverlay {
    private static final float REFERENCE_HEIGHT = 1080.0f;
    private static boolean debugPrinted = false;

    private static class RenderCache {
        Identifier id;
        MediaTextureWrapper wrapper;

        void free() {
            if (id != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
                id = null;
            }
        }
    }

    private static final Map<AbstractMedia, RenderCache> RENDER_CACHES = new HashMap<>();

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

            for (AbstractMedia media : medias) {
                int textureId = media.getGlId(tickCounter.getTickProgress(true));
                if (textureId <= 0) continue;

                int mediaWidth = media.getWidth();
                int mediaHeight = media.getHeight();
                if (mediaWidth <= 0 || mediaHeight <= 0) continue;

                RenderCache cache = RENDER_CACHES.computeIfAbsent(media, k -> {
                    RenderCache c = new RenderCache();
                    c.id = Identifier.of("vctmedia", "media_frame_" + System.nanoTime());
                    c.wrapper = new MediaTextureWrapper();
                    MinecraftClient.getInstance().getTextureManager().registerTexture(c.id, c.wrapper);
                    return c;
                });

                // Pasamos el ID, anchura y altura al wrapper para que construya el GlTexture interno sin bloqueos
                cache.wrapper.updateTexture(textureId, mediaWidth, mediaHeight);

                float alpha = media.opacity / 100.0f;
                // Usamos ARGB puro para no perder el color
                int color = ((int)(alpha * 255.0f) & 0xFF) << 24 | 0xFFFFFF;

                context.getMatrices().pushMatrix();
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

                RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

                // Utilizamos el renderizado que ya tenías y funcionaba sin problemas matemáticos
                context.drawTexture(pipeline, cache.id, (int) x, (int) y, 0f, 0f, (int) width, (int) height, mediaWidth, mediaHeight, mediaWidth, mediaHeight, color);

                if (!debugPrinted) {
                    System.out.println("[ViciontMedia] Render finalizado en pantalla con éxito mediante drawTexture nativo.");
                    debugPrinted = true;
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