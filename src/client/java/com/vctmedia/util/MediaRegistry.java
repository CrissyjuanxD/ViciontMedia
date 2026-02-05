package com.vctmedia.util;

import com.vctmedia.ViciontMediaClient;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class MediaRegistry {
    // Solo extensiones de imagen para evitar saturar RAM con videos
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".gif", ".png", ".jpg", ".jpeg");

    public static void initPreload() {
        File folder = ViciontMediaClient.MEDIA_DIR.toFile();
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                // Solo precargamos si es una imagen/gif
                if (IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith)) {
                    MediaOrchestrator.preload(file.getName());
                }
            }
        }
    }
}