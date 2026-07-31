package com.vctmedia.render;

import com.vctmedia.ViciontMediaClient;
import com.vctmedia.util.FadeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
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

    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedTexture = -1;
    private Boolean cachedHasVideo = null;
    private long lastTexturePollMs = 0;
    private boolean loadFailed = false;
    private long loadStartTime = 0;
    private static final long LOAD_TIMEOUT_MS = 15000;

    // Polling adaptativo: si el frame tardo mucho, dar mas tiempo antes del siguiente poll
    private static final long POLL_MIN_MS = 33;   // ~30fps en condiciones normales
    private static final long POLL_MAX_MS = 100;  // maximo si el frame fue pesado

    public ImageMedia(String url, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, boolean useFade) {
        super(url, soundId, duration, size, pos, opacity, isOverlay, useFade);
    }

    @Override
    public void loadAsync() {
        if (loading) return;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                if (released) return;

                URI uri = url.startsWith("http")
                        ? URI.create(url)
                        : ViciontMediaClient.MEDIA_DIR.resolve(url).toUri();

                loadStartTime = System.currentTimeMillis();
                mrl = MediaAPI.mrl(uri);

                if (mrl == null) {
                    loadFailed = true;
                    notifyError("No se pudo abrir el medio (MRL nulo): " + url);
                    return;
                }

                mrl.subscribe(loaded -> MinecraftClient.getInstance().execute(this::createPlayer));

                this.startTime = System.currentTimeMillis();
                if (duration >= 1000) this.endTime = startTime + duration;
            } catch (Exception e) {
                loadFailed = true;
                notifyError("Error al cargar: " + e.getMessage());
                e.printStackTrace();
            }
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
            } else {
                loadFailed = true;
                notifyError("MediaPlayer no pudo ser creado: " + url);
            }
        } catch (Exception e) {
            loadFailed = true;
            notifyError("Error al crear player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void notifyError(String msg) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§c[ViciontMedia] §f" + msg), false);
            }
            System.err.println("[ViciontMedia] " + msg);
        });
    }

    private boolean hasVideo() {
        if (cachedHasVideo != null) return cachedHasVideo;
        if (player == null) return false;
        cachedHasVideo = player.withVideo();
        return cachedHasVideo;
    }

    @Override
    public int getGlId(float partialTick) {
        if (released) return -1;
        if (loadFailed) return -1;

        if (useFade && size <= 0 && !triggeredEndFade) {
            if (endTime != -1) {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= FadeManager.END_FADE_IN + (FadeManager.END_FADE_STAY / 2)) {
                    FadeManager.triggerEndFadeNow();
                    triggeredEndFade = true;
                }
            }
        }

        if (player != null && hasVideo()) {
            long now = System.currentTimeMillis();

            // Polling adaptativo: si paso mucho tiempo desde el ultimo poll, esperar mas
            long elapsed = now - lastTexturePollMs;
            long interval = (lastTexturePollMs > 0 && elapsed > POLL_MAX_MS) ? POLL_MAX_MS : POLL_MIN_MS;

            if (elapsed >= interval) {
                lastTexturePollMs = now;

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
                if (tex > 0) {
                    cachedTexture = (int) tex;
                    // El video aparecio — avisar al fade para que empiece a salir del negro
                    if (useFade && size <= 0) {
                        FadeManager.notifyVideoReady();
                    }
                }
            }
            return cachedTexture;
        }
        return -1;
    }

    @Override
    public int getWidth() {
        if (cachedWidth > 0) return cachedWidth;
        if (player != null) {
            int w = player.width();
            if (w > 0) cachedWidth = w;
            return w > 0 ? w : 1;
        }
        return 1;
    }

    @Override
    public int getHeight() {
        if (cachedHeight > 0) return cachedHeight;
        if (player != null) {
            int h = player.height();
            if (h > 0) cachedHeight = h;
            return h > 0 ? h : 1;
        }
        return 1;
    }

    @Override
    protected boolean checkSpecificExpired() {
        if (loadFailed) return true;
        if (mrl != null && !mrl.status().loaded() && startTime == -1) {
            if (System.currentTimeMillis() - loadStartTime > LOAD_TIMEOUT_MS) {
                loadFailed = true;
                notifyError("Timeout al cargar el medio (15s): " + url);
                return true;
            }
        }
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
