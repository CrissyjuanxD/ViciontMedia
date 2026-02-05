package com.vctmedia.util;

import com.vctmedia.render.TextureWrapper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MediaOrchestrator {
    private static final List<TextureWrapper> activeMedias = new CopyOnWriteArrayList<>();

    public static void process(String url, long duration, int x, int y, int size, boolean isOverlay) {
        if (!isOverlay) {
            // Solo borramos los que NO son overlay
            activeMedias.removeIf(m -> {
                if (!m.isOverlay) {
                    m.release();
                    return true;
                }
                return false;
            });
        }
        TextureWrapper media = new TextureWrapper(url, duration, x, y, size, isOverlay);
        media.loadAsync();
        activeMedias.add(media);
    }

    public static void edit(String name, long duration, int x, int y, int size, boolean overlay) {
        for (TextureWrapper m : activeMedias) {
            if (m.url.contains(name)) {
                m.x = x;
                m.y = y;
                m.size = size;
                m.isOverlay = overlay;
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