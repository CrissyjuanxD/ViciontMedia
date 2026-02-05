package com.vctmedia.util;

import com.vctmedia.ViciontMediaClient;
import com.vctmedia.render.TextureWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageCache;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.core.tools.IOTool;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class MediaOrchestrator {
    private static TextureWrapper currentMedia;
    public static final Identifier DYNAMIC_ID = Identifier.of("vctmedia", "active_media");

    public static int posX, posY, renderSize;

    public static void process(String url, long duration, int x, int y, int size) {
        stopAll();
        posX = x; posY = y; renderSize = size;

        // Estructura tipo Sticker: Creamos el wrapper vacío y le ordenamos cargar
        currentMedia = new TextureWrapper(url, duration);
        currentMedia.loadAsync();
    }

    public static void stopAll() {
        if (currentMedia != null) {
            currentMedia.release();
            currentMedia = null;
        }
    }

    public static TextureWrapper getActive() {
        if (currentMedia != null && currentMedia.isExpired()) {
            stopAll();
            return null;
        }
        return currentMedia;
    }
}