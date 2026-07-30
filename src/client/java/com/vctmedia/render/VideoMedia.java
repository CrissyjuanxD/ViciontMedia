package com.vctmedia.render;

import net.minecraft.client.MinecraftClient;
import org.watermedia.api.player.videolan.VideoPlayer;
import com.vctmedia.ViciontMediaClient;
import com.vctmedia.util.VolumeManager;
import com.vctmedia.util.FadeManager;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class VideoMedia extends AbstractMedia {
    public VideoPlayer video;

    public VideoMedia(String url, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, boolean useFade) {
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

                MinecraftClient.getInstance().execute(() -> {
                    this.video = new VideoPlayer(MinecraftClient.getInstance());
                    this.video.start(url.startsWith("http") ? URI.create(url) : ViciontMediaClient.MEDIA_DIR.resolve(url).toUri());
                    this.video.setVolume(VolumeManager.getVolume());
                });

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
            long remaining = -1;
            if (maxLoops == -1 && video != null) {
                long durationMs = video.getDuration();
                if (durationMs > 0) remaining = durationMs - video.getTime();
            } else if (endTime != -1) {
                remaining = endTime - System.currentTimeMillis();
            }

            if (remaining != -1 && remaining <= FadeManager.FADE_IN + (FadeManager.FADE_STAY / 2)) {
                FadeManager.triggerEndFadeNow();
                triggeredEndFade = true;
            }
        }

        if (video != null) {
            video.setVolume(VolumeManager.getVolume());
            if (video.isReady()) return video.texture();
        }
        return -1;
    }

    @Override
    public int getWidth() { return video != null ? video.width() : 1; }

    @Override
    public int getHeight() { return video != null ? video.height() : 1; }

    @Override
    protected boolean checkSpecificExpired() {
        return video != null && video.isEnded();
    }

    @Override
    public void release() {
        this.released = true;
        if (this.video != null) {
            this.video.release();
            this.video = null;
        }
    }
}