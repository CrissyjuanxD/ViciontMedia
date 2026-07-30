package com.vctmedia.util;

import com.vctmedia.render.AbstractMedia;
import com.vctmedia.render.ImageMedia;
import com.vctmedia.render.VideoMedia;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MediaOrchestrator {
    private static final List<AbstractMedia> activeMedias = new CopyOnWriteArrayList<>();

    private static boolean isYoutubeUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("youtube.com/watch") || lower.contains("youtu.be/")
                || lower.contains("youtube.com/shorts") || lower.contains("m.youtube.com");
    }

    private static boolean isPlatformExtensionInstalled() {
        return FabricLoader.getInstance().isModLoaded("watermedia_youtube_extension")
                || FabricLoader.getInstance().isModLoaded("watermedia_pe")
                || FabricLoader.getInstance().isModLoaded("watermedia_yt_plugin");
    }

    private static void notifyPlayer(String msg) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal(msg));
            }
            System.err.println("[ViciontMedia] " + Text.literal(msg).getString());
        });
    }

    public static void process(String url, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, boolean useFade) {
        String lower = url.toLowerCase();

        if (isYoutubeUrl(url) && !isPlatformExtensionInstalled()) {
            notifyPlayer("§c[ViciontMedia] §fLos links de YouTube requieren el mod §eWATERMeDIA Platform Extension§f (complemento de YouTube). Descárgalo de Modrinth: watermedia-yt-plugin");
            return;
        }

        boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");

        boolean hasVideoActive = activeMedias.stream().anyMatch(m -> m instanceof VideoMedia);
        if (!hasVideoActive && isImage) {
            VolumeManager.setVolume(70);
        }

        if (!isOverlay) {
            for (AbstractMedia m : activeMedias) {
                if (!m.isOverlay) {
                    m.stopGracefully();
                }
            }
        }

        if (useFade && size <= 0) {
            FadeManager.startFade(duration);
        }

        AbstractMedia media;
        if (isImage) {
            media = new ImageMedia(url, soundId, duration, size, pos, opacity, isOverlay, useFade);
        } else {
            media = new VideoMedia(url, soundId, duration, size, pos, opacity, isOverlay, useFade);
        }

        activeMedias.add(media);
        media.loadAsync();
    }

    public static void edit(String name, long duration, int size, String pos, int opacity, boolean overlay) {
        for (AbstractMedia m : activeMedias) {
            if (m.url.contains(name)) {
                m.size = size; m.pos = pos; m.opacity = opacity; m.isOverlay = overlay;
                m.updateLoopLogic(duration);
            }
        }
    }

    public static void stopAll() {
        for (AbstractMedia m : activeMedias) {
            m.stopGracefully();
        }
    }

    public static void stopSpecific(String name) {
        for (AbstractMedia m : activeMedias) {
            if (m.url.contains(name)) {
                m.stopGracefully();
            }
        }
    }

    public static List<AbstractMedia> getActiveList() {
        activeMedias.removeIf(m -> {
            if (m.isExpired()) {
                m.release();
                return true;
            }
            return false;
        });
        return activeMedias;
    }
}
