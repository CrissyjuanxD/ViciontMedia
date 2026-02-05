package com.vctmedia.render;

import net.minecraft.client.MinecraftClient;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageCache;
import org.watermedia.api.image.ImageRenderer;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.core.tools.IOTool;
import com.vctmedia.ViciontMediaClient;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import java.io.File;

public class TextureWrapper {
    public final String url;
    // Campos mutables para permitir vctedit
    public long duration;
    public int x, y, size;
    public boolean isOverlay;

    private ImageRenderer gif;
    private VideoPlayer video;
    private ImageCache cache;
    private long endTime = -1;
    private long startTime = -1;
    private int maxLoops = -1;
    private boolean loading = false;

    public TextureWrapper(String url, long duration, int x, int y, int size, boolean isOverlay) {
        this.url = url;
        this.duration = duration;
        this.x = x;
        this.y = y;
        this.size = size;
        this.isOverlay = isOverlay;
        updateLoopLogic(duration);
    }

    public void updateLoopLogic(long newDuration) {
        this.duration = newDuration;
        if (newDuration > 0 && newDuration < 1000) {
            this.maxLoops = (int) newDuration;
            this.endTime = -1;
        } else if (newDuration >= 1000) {
            this.maxLoops = -1;
            if (startTime != -1) this.endTime = startTime + newDuration;
        } else {
            this.maxLoops = -1;
            this.endTime = -1;
        }
    }

    public void loadAsync() {
        if (loading) return;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                String lower = url.toLowerCase();
                boolean isImage = lower.endsWith(".gif") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                if (isImage) {
                    if (url.startsWith("http")) {
                        this.cache = ImageAPI.getCache(URI.create(url), MinecraftClient.getInstance());
                    } else {
                        File file = ViciontMediaClient.MEDIA_DIR.resolve(url).toFile();
                        if (file.exists()) {
                            if (lower.endsWith(".gif")) {
                                var gifData = IOTool.readGif(file.toPath().toAbsolutePath());
                                if (gifData != null) this.gif = ImageAPI.renderer(gifData);
                            } else {
                                var image = ImageIO.read(file);
                                if (image != null) this.gif = ImageAPI.renderer(image);
                            }
                        }
                    }
                } else {
                    MinecraftClient.getInstance().execute(() -> {
                        this.video = new VideoPlayer(MinecraftClient.getInstance());
                        this.video.start(url.startsWith("http") ? URI.create(url) : ViciontMediaClient.MEDIA_DIR.resolve(url).toUri());
                    });
                }
                this.startTime = System.currentTimeMillis();
                if (duration >= 1000) this.endTime = startTime + duration;
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public int getGlId(float partialTick) {
        if (cache != null && cache.getStatus() == ImageCache.Status.READY) {
            this.gif = cache.getRenderer();
            this.cache = null;
        }
        if (gif != null) {
            long time = System.currentTimeMillis() - startTime;
            // CORRECCIÓN GIF: Si es bucle o quedan vueltas, usamos el módulo para reiniciar el frame
            if (gif.duration > 0) {
                time = time % gif.duration;
            }
            return gif.texture(time);
        }
        if (video != null && video.isReady()) return video.texture();
        return -1;
    }

    public boolean isExpired() {
        if (startTime == -1) return false;
        if (maxLoops != -1 && gif != null && gif.duration > 0) {
            if ((System.currentTimeMillis() - startTime) / gif.duration >= maxLoops) return true;
        }
        if (endTime != -1 && System.currentTimeMillis() > endTime) return true;
        return video != null && video.isEnded();
    }

    public int getWidth() { return gif != null ? gif.width : (video != null ? video.width() : 1); }
    public int getHeight() { return gif != null ? gif.height : (video != null ? video.height() : 1); }
    public void release() { if (gif != null) gif.release(); if (video != null) { video.stop(); video.release(); } }
}