/*

ESTA CLASE ESTA COMENTADA PORQUE ERA PROBLEMATICA, ASI QUE FUE REWORKEADA A MediaOrchestrator
tengo que norrar esta clase sjaksajs en un futuro.

package com.vctmedia;

import com.vctmedia.render.TextureWrapper;
import net.minecraft.client.MinecraftClient;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageCache;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.core.tools.IOTool;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MediaManager {
    private static final TextureWrapper wrapper = new TextureWrapper();
    private static ImageCache activeCache;
    public static long startTime = 0;
    private static long playbackEndTime = 0;
    private static boolean isInfinite = false;
    private static int gifRepetitions = -1;
    public static int posX, posY, renderSize;

    public static void handlePacket(byte action, String pathOrUrl, long duration, int x, int y, int size) {
        if (action == 0) { stop(); return; }
        stop();

        posX = x; posY = y; renderSize = size;
        configureTiming(duration);

        String path = pathOrUrl.toLowerCase(Locale.ROOT);
        MinecraftClient client = MinecraftClient.getInstance();

        CompletableFuture.runAsync(() -> {
            try {
                if (path.endsWith(".gif") || path.endsWith(".png") || path.endsWith(".jpg")) {
                    if (pathOrUrl.startsWith("http")) {
                        client.execute(() -> activeCache = ImageAPI.getCache(URI.create(pathOrUrl), client));
                    } else {
                        var data = IOTool.readGif(ViciontMediaClient.MEDIA_DIR.resolve(pathOrUrl).toAbsolutePath());
                        if (data != null) client.execute(() -> {
                            wrapper.setGif(ImageAPI.renderer(data));
                            startTime = System.currentTimeMillis();
                        });
                    }
                } else {
                    client.execute(() -> {
                        VideoPlayer v = new VideoPlayer(client);
                        v.start(pathOrUrl.startsWith("http") ? URI.create(pathOrUrl) : ViciontMediaClient.MEDIA_DIR.resolve(pathOrUrl).toUri());
                        wrapper.setVideo(v);
                        startTime = System.currentTimeMillis();
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private static void configureTiming(long d) {
        if (d == -1) { isInfinite = true; gifRepetitions = -1; }
        else if (d < 1000) { isInfinite = false; gifRepetitions = (int) d; }
        else { isInfinite = false; gifRepetitions = -1; playbackEndTime = System.currentTimeMillis() + d; }
    }

    public static void stop() {
        MinecraftClient.getInstance().execute(() -> {
            wrapper.release();
            activeCache = null;
            startTime = 0;
            playbackEndTime = 0;
        });
    }

    public static TextureWrapper getWrapper() {
        if (activeCache != null && activeCache.getStatus() == ImageCache.Status.READY) {
            wrapper.setGif(activeCache.getRenderer());
            activeCache = null;
            startTime = System.currentTimeMillis();
        }

        if (!isInfinite && startTime > 0) {
            if (playbackEndTime > 0 && System.currentTimeMillis() > playbackEndTime) { stop(); return null; }
            if (gifRepetitions > 0 && (System.currentTimeMillis() - startTime) >= (wrapper.getHeight() > 0 ? 5000 : 0)) { // Ejemplo simplificado

            }
        }
        return wrapper;
    }
}*/
