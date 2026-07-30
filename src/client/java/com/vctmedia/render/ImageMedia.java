package com.vctmedia.render;

import com.vctmedia.util.GifPreCache;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageRenderer;
import org.watermedia.core.tools.IOTool;
import com.mojang.blaze3d.platform.GlStateManager;
import com.vctmedia.ViciontMediaClient;
import com.vctmedia.util.FadeManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class ImageMedia extends AbstractMedia {
    private ImageRenderer imageRenderer;

    public ImageMedia(String url, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, boolean useFade) {
        super(url, soundId, duration, size, pos, opacity, isOverlay, useFade);
    }

    @Override
    public void loadAsync() {
        if (loading) return;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                if (useFade && size <= 0) {
                    Thread.sleep(FadeManager.FADE_IN + (FadeManager.FADE_STAY / 2));
                }
                if (released) return;

                String lower = url.toLowerCase();
                boolean isGif = lower.endsWith(".gif");

                if (url.startsWith("http")) {
                    BufferedImage image = ImageIO.read(new URL(url));
                    if (image != null && !released) {
                        this.imageRenderer = ImageAPI.renderer(image);
                    }
                } else {
                    File file = ViciontMediaClient.MEDIA_DIR.resolve(url).toFile();
                    if (file.exists()) {
                        if (isGif) {
                            String filename = file.getName();
                            ImageRenderer cached = GifPreCache.get(filename);
                            if (cached != null) {
                                this.imageRenderer = cached;
                            } else {
                                var gifData = IOTool.readGif(file.toPath().toAbsolutePath());
                                if (gifData != null && !released) {
                                    this.imageRenderer = ImageAPI.renderer(gifData);
                                }
                            }
                        } else {
                            BufferedImage image = ImageIO.read(file);
                            if (image != null && !released) {
                                this.imageRenderer = ImageAPI.renderer(image);
                            }
                        }
                    }
                }

                playMcSound();
                this.startTime = System.currentTimeMillis();
                if (duration >= 1000) this.endTime = startTime + duration;
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @Override
    public int getGlId(float partialTick) {
        if (released) return -1;

        if (useFade && size <= 0 && !triggeredEndFade) {
            if (endTime != -1) {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= FadeManager.FADE_IN + (FadeManager.FADE_STAY / 2)) {
                    FadeManager.triggerEndFadeNow();
                    triggeredEndFade = true;
                }
            }
        }

        if (imageRenderer != null) {
            long effectiveStart = (startTime == -1) ? System.currentTimeMillis() : startTime;
            long time = System.currentTimeMillis() - effectiveStart;

            if (imageRenderer.duration > 0) time = time % imageRenderer.duration;
            return imageRenderer.texture(time);
        }
        return -1;
    }

    @Override
    public int getWidth() { return imageRenderer != null ? imageRenderer.width : 1; }

    @Override
    public int getHeight() { return imageRenderer != null ? imageRenderer.height : 1; }

    @Override
    protected boolean checkSpecificExpired() {
        if (maxLoops != -1 && imageRenderer != null && imageRenderer.duration > 0) {
            if ((System.currentTimeMillis() - startTime) / imageRenderer.duration >= maxLoops) return true;
        }
        return false;
    }

    @Override
    public void release() {
        this.released = true;
        if (this.imageRenderer != null) {
            try {
                String filename = "";
                if (url != null && !url.startsWith("http")) {
                    filename = new File(url).getName();
                }

                if (!filename.isEmpty() && GifPreCache.isReady(filename)) {
                } else {
                    if (this.imageRenderer.textures != null) {
                        GlStateManager._deleteTextures(this.imageRenderer.textures);
                    }
                    this.imageRenderer.release();
                }
            } catch (Exception ignored) {}
            this.imageRenderer = null;
        }
    }
}