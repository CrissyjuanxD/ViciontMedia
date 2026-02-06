package com.vctmedia.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.random.Random;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageCache;
import org.watermedia.api.image.ImageRenderer;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.core.tools.IOTool;
import com.vctmedia.ViciontMediaClient;
import com.vctmedia.util.VolumeManager;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import java.io.File;

public class TextureWrapper {
    public final String url;
    public String soundId;
    public long duration;
    public int x, y, size;
    public boolean isOverlay;

    private ImageRenderer gif;
    public VideoPlayer video;
    private ImageCache cache;
    private long endTime = -1;
    private long startTime = -1;
    private int maxLoops = -1;
    private boolean loading = false;

    public TextureWrapper(String url, String soundId, long duration, int x, int y, int size, boolean isOverlay) {
        this.url = url;
        this.soundId = soundId;
        this.duration = duration;
        this.x = x;
        this.y = y;
        this.size = size;
        this.isOverlay = isOverlay;
        updateLoopLogic(duration);
    }

    // Añadimos el parámetro newSoundId
    public TextureWrapper createActiveCopy(String newSoundId, long duration, int x, int y, int size, boolean isOverlay) {
        // Pasamos newSoundId al constructor en lugar de this.soundId (que podría ser null)
        TextureWrapper copy = new TextureWrapper(this.url, newSoundId, duration, x, y, size, isOverlay);

        if (this.gif != null) {
            copy.gif = this.gif;
            copy.startTime = System.currentTimeMillis();
            copy.updateLoopLogic(duration);

            // Ahora sí, al reproducir, copy.soundId ya tiene el valor correcto
            copy.playMcSound();
        } else {
            copy.loadAsync();
        }

        return copy;
    }

    public void updateLoopLogic(long newDuration) {
        this.duration = newDuration;
        if (newDuration > 0 && newDuration < 1000) this.maxLoops = (int) newDuration;
        else if (newDuration >= 1000 && startTime != -1) this.endTime = startTime + newDuration;
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
                        this.video.setVolume(VolumeManager.getVolume());
                    });
                }
                playMcSound();
                this.startTime = System.currentTimeMillis();
                if (duration >= 1000) this.endTime = startTime + duration;
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void playMcSound() {
        if (soundId == null || soundId.trim().isEmpty()) return;

        MinecraftClient.getInstance().execute(() -> {
            try {
                System.out.println("[ViciontMedia-DEBUG] Intentando reproducir ID: " + soundId); // LOG 1

                Identifier id = Identifier.tryParse(soundId);
                if (id == null) {
                    System.err.println("[ViciontMedia-ERROR] ID de sonido invalido: " + soundId);
                    return;
                }

                // Usamos SoundEvent.of directamente.
                // Registries.SOUND_EVENT.get puede fallar con sonidos de resource packs no registrados en código.
                SoundEvent soundEvent = SoundEvent.of(id);

                // --- REWORK CRITICO ---
                // Forzamos el uso de MASTER y sonido global (sin posición) temporalmente.
                // Esto descarta problemas de distancia, coordenadas erróneas o categorías de sonido (Jugadores/Bloques) silenciadas.
                // Usamos pitch 1.0 y volumen 2.0 (saturado para asegurar que se escuche si está bajo)

                PositionedSoundInstance soundInstance = PositionedSoundInstance.master(soundEvent, 1.0f, 2.0f);

                if (MinecraftClient.getInstance().getSoundManager().isPlaying(soundInstance)) {
                    System.out.println("[ViciontMedia-DEBUG] El sonido ya se está reproduciendo (spam?)");
                }

                MinecraftClient.getInstance().getSoundManager().play(soundInstance);
                System.out.println("[ViciontMedia-DEBUG] Orden enviada al SoundManager para: " + id); // LOG 2

            } catch (Exception e) {
                System.err.println("[ViciontMedia-ERROR] Excepcion al reproducir: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    public int getGlId(float partialTick) {
        if (cache != null && cache.getStatus() == ImageCache.Status.READY) {
            this.gif = cache.getRenderer();
            this.cache = null;
        }
        if (video != null) video.setVolume(VolumeManager.getVolume());
        if (gif != null) {
            // Si startTime es -1 (error de carga), usamos el tiempo actual para evitar crashes matemáticos
            long effectiveStart = (startTime == -1) ? System.currentTimeMillis() : startTime;
            long time = System.currentTimeMillis() - effectiveStart;
            if (gif.duration > 0) time = time % gif.duration;
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
    public void release() {
        if (this.video != null) {
            this.video.release();
            this.video = null;
        }
        this.gif = null;
    }
}