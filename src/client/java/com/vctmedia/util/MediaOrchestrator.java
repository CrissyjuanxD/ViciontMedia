package com.vctmedia.util;

import com.vctmedia.render.TextureWrapper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MediaOrchestrator {
    private static final List<TextureWrapper> activeMedias = new CopyOnWriteArrayList<>();
    private static final List<TextureWrapper> preloadedMedias = new CopyOnWriteArrayList<>();

    public static void preload(String fileName) {
        if (preloadedMedias.stream().anyMatch(m -> m.url.equals(fileName))) return;

        // Creamos el "molde" original
        TextureWrapper media = new TextureWrapper(fileName, null, -1, 0, 0, 0, true);
        media.loadAsync();
        preloadedMedias.add(media);
    }

    public static void process(String url, String soundId, long duration, int x, int y, int size, boolean isOverlay) {
        // Lógica de reinicio de volumen a 70%
        boolean hasVideoActive = activeMedias.stream().anyMatch(m -> m.video != null);
        if (!hasVideoActive && !url.toLowerCase().matches(".*\\.(gif|png|jpg|jpeg)$")) {
            VolumeManager.setVolume(70);
        }

        if (!isOverlay) {
            activeMedias.removeIf(m -> {
                if (!m.isOverlay) { m.release(); return true; }
                return false;
            });
        }

        TextureWrapper template = preloadedMedias.stream()
                .filter(m -> m.url.equals(url))
                .findFirst().orElse(null);

        if (template != null) {
            TextureWrapper copy = template.createActiveCopy(soundId, duration, x, y, size, isOverlay);
            activeMedias.add(copy);
        } else {
            TextureWrapper media = new TextureWrapper(url, soundId, duration, x, y, size, isOverlay);
            media.loadAsync();
            activeMedias.add(media);
        }
    }

    public static void edit(String name, long duration, int x, int y, int size, boolean overlay) {
        for (TextureWrapper m : activeMedias) {
            if (m.url.contains(name)) {
                m.x = x; m.y = y; m.size = size; m.isOverlay = overlay;
                m.updateLoopLogic(duration);
            }
        }
    }

    public static void stopAll() {
        for (TextureWrapper m : activeMedias) m.release();
        activeMedias.clear();
    }

    public static void stopSpecific(String name) {
        activeMedias.removeIf(m -> {
            if (m.url.contains(name)) { m.release(); return true; }
            return false;
        });
    }

    public static List<TextureWrapper> getActiveList() {
        activeMedias.removeIf(m -> {
            if (m.isExpired()) { m.release(); return true; }
            return false;
        });
        return activeMedias;
    }
}