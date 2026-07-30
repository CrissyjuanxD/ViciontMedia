package com.vctmedia.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import com.vctmedia.util.FadeManager;

public abstract class AbstractMedia {
    public String url;
    public String soundId;
    public long duration;
    public int size;
    public String pos;
    public int opacity;
    public boolean isOverlay;
    public boolean useFade;

    public long endTime = -1;
    public long startTime = -1;
    public int maxLoops = -1;
    public boolean loading = false;
    public boolean released = false;
    public boolean triggeredEndFade = false;

    public AbstractMedia(String url, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, boolean useFade) {
        this.url = url;
        this.soundId = soundId;
        this.duration = duration;
        this.size = size;
        this.pos = pos;
        this.opacity = opacity;
        this.isOverlay = isOverlay;
        this.useFade = useFade;
        updateLoopLogic(duration);
    }

    public void updateLoopLogic(long newDuration) {
        this.duration = newDuration;
        if (newDuration > 0 && newDuration < 1000) this.maxLoops = (int) newDuration;
        else if (newDuration >= 1000 && startTime != -1) this.endTime = startTime + newDuration;
    }

    public abstract void loadAsync();
    public abstract int getGlId(float partialTick);
    public abstract int getWidth();
    public abstract int getHeight();
    public abstract void release();

    public void stopGracefully() {
        if (useFade && size <= 0) {
            if (!triggeredEndFade) {
                FadeManager.triggerEndFadeNow();
                triggeredEndFade = true;
            }
            long expireTime = System.currentTimeMillis() + FadeManager.END_FADE_IN + (FadeManager.END_FADE_STAY / 2);
            if (endTime == -1 || endTime > expireTime) {
                endTime = expireTime;
            }
        } else {
            endTime = System.currentTimeMillis();
        }
    }

    public boolean isExpired() {
        if (released) return true;
        if (startTime == -1) return false;
        if (endTime != -1 && System.currentTimeMillis() > endTime) return true;
        return checkSpecificExpired();
    }

    protected abstract boolean checkSpecificExpired();

    protected void playMcSound() {
        if (soundId == null || soundId.trim().isEmpty()) return;

        MinecraftClient.getInstance().execute(() -> {
            try {
                Identifier id = Identifier.tryParse(soundId);
                if (id != null) {
                    SoundEvent soundEvent = SoundEvent.of(id);
                    PositionedSoundInstance soundInstance = PositionedSoundInstance.master(soundEvent, 1.0f, 2.0f);
                    MinecraftClient.getInstance().getSoundManager().play(soundInstance);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}