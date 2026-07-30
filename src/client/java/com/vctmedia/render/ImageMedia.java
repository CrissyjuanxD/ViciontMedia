package com.vctmedia.render;

import com.vctmedia.ViciontMediaClient;
import com.vctmedia.util.FadeManager;
import net.minecraft.client.MinecraftClient;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.players.MediaPlayer;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class ImageMedia extends AbstractMedia {
    private MediaPlayer player;
    private MRL mrl;
    private int loopCount = 0;
    private long lastTime = -1;

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

                URI uri = url.startsWith("http")
                        ? URI.create(url)
                        : ViciontMediaClient.MEDIA_DIR.resolve(url).toUri();

                mrl = MediaAPI.mrl(uri);

                mrl.subscribe(loaded -> MinecraftClient.getInstance().execute(this::createPlayer));

                this.startTime = System.currentTimeMillis();
                if (duration >= 1000) this.endTime = startTime + duration;
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void createPlayer() {
        if (released || player != null) return;
        if (mrl == null || !mrl.status().loaded()) return;

        try {
            Thread renderThread = Thread.currentThread();
            MinecraftClient mc = MinecraftClient.getInstance();
            player = MediaAPI.createPlayer(mrl, 0,
                    () -> MediaAPI.glEngine(renderThread, mc::execute),
                    () -> null);

            if (player != null) {
                boolean shouldRepeat = (maxLoops == -1 || maxLoops > 1);
                player.repeat(shouldRepeat);
                player.start();
                playMcSound();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        if (player != null && player.withVideo()) {
            if (maxLoops > 1) {
                long currentTime = player.time();
                if (lastTime > 0 && currentTime < lastTime) {
                    loopCount++;
                    if (loopCount >= maxLoops - 1) {
                        player.repeat(false);
                    }
                }
                lastTime = currentTime;
            }

            long tex = player.texture();
            return tex > 0 ? (int) tex : -1;
        }
        return -1;
    }

    @Override
    public int getWidth() {
        return player != null ? player.width() : 1;
    }

    @Override
    public int getHeight() {
        return player != null ? player.height() : 1;
    }

    @Override
    protected boolean checkSpecificExpired() {
        return player != null && player.ended();
    }

    @Override
    public void release() {
        this.released = true;
        if (this.player != null) {
            try {
                this.player.release();
            } catch (Exception ignored) {}
            this.player = null;
        }
    }
}
