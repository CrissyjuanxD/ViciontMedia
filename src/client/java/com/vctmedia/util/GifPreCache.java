package com.vctmedia.util;

/*
 * GifPreCache — DESACTIVADO en WaterMedia v3.
 *
 * En v2 esto era necesario porque los GIFs se decodificaban completos en RAM
 * antes de mostrarse, y la pre-carga evitaba el retardo inicial. Con v3,
 * TxMediaPlayer decodifica los fotogramas bajo demanda (streaming), por lo que
 * el primer fotograma aparece casi instantaneamente sin necesidad de pre-cargar.
 *
 * Se mantiene el codigo comentado por si en el futuro se quiere reactivar
 * para casos especificos (ej. GIFs muy grandes en red lenta).
 */

import com.vctmedia.ViciontMediaClient;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.MRL;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GifPreCache {
    private static final Map<String, MRL> cache = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void init() {
        // DESACTIVADO en v3 — la decodificacion bajo demanda hace innecesaria la pre-carga.
        /*
        if (initialized) return;
        initialized = true;

        Thread thread = new Thread(() -> {
            try {
                File dir = ViciontMediaClient.MEDIA_DIR.toFile();
                if (!dir.exists() || !dir.isDirectory()) return;

                File[] gifs = dir.listFiles(f ->
                        f.isFile() &&
                                f.getName().toLowerCase().endsWith(".gif") &&
                                f.getName().toLowerCase().contains("_pre")
                );

                if (gifs == null || gifs.length == 0) return;

                for (File gif : gifs) {
                    if (cache.containsKey(gif.getName())) continue;
                    try {
                        URI uri = gif.toURI();
                        MRL mrl = MediaAPI.mrl(uri);
                        cache.put(gif.getName(), mrl);
                        System.out.println("[VctMedia] GIF MRL pre-cargado: " + gif.getName());
                        Thread.sleep(500);
                    } catch (Exception e) {
                        System.err.println("[VctMedia] Error pre-cargando " + gif.getName() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "VctMedia-GifPreCache");

        thread.setDaemon(true);
        thread.start();
        */
    }

    public static MRL get(String filename) {
        return cache.get(filename);
    }

    public static boolean isReady(String filename) {
        MRL mrl = cache.get(filename);
        return mrl != null && mrl.status().loaded();
    }

    public static void evict(String filename) {
        cache.remove(filename);
    }

    public static void evictAll() {
        cache.clear();
        initialized = false;
    }
}
