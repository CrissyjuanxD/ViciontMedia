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
    private final String url;
    private final long duration;
    private ImageRenderer gif;
    private VideoPlayer video;
    private ImageCache cache;
    private long endTime = -1;
    private long startTime = -1; // Para sincronización precisa
    private boolean loading = false;

    public TextureWrapper(String url, long duration) {
        this.url = url;
        this.duration = duration;
    }

    public void loadAsync() {
        if (loading) return;
        loading = true;

        CompletableFuture.runAsync(() -> {
            try {
                String lower = url.toLowerCase();
                boolean isImage = lower.endsWith(".gif") || lower.endsWith(".png") ||
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg");

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
                if (duration > 0) this.endTime = startTime + duration;
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public int getGlId(float partialTick) {
        if (cache != null && cache.getStatus() == ImageCache.Status.READY) {
            this.gif = cache.getRenderer();
            this.cache = null;
        }

        if (gif != null) {
            // Lógica de Owleaf: Tiempo transcurrido desde el inicio del objeto
            return gif.texture(System.currentTimeMillis() - startTime);
        }
        if (video != null && video.isReady()) return video.texture();
        return -1;
    }

    public int getWidth() {
        if (gif != null) return gif.width;
        if (video != null && video.isReady()) return video.width();
        return 1;
    }

    public int getHeight() {
        if (gif != null) return gif.height;
        if (video != null && video.isReady()) return video.height();
        return 1;
    }

    public boolean isExpired() {
        if (startTime == -1) return false; // Aún cargando
        if (endTime != -1 && System.currentTimeMillis() > endTime) return true;
        return video != null && video.isEnded();
    }

    public void release() {
        if (gif != null) gif.release();
        if (video != null) { video.stop(); video.release(); }
    }
}