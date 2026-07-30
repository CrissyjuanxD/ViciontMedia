package com.vctmedia.util;

import com.vctmedia.ViciontMediaClient;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageRenderer;
import org.watermedia.core.tools.IOTool;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GifPreCache {

    // El único mapa de GIFs pre-cargados. Clave = nombre del archivo (ej: "intro_pre.gif")
    private static final Map<String, ImageRenderer> cache = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    /**
     * Llámalo UNA vez al iniciar el cliente.
     * Escanea MEDIA_DIR, encuentra los *_pre.gif y los carga uno por uno
     * con un pequeño delay entre cada uno para no explotar la RAM de golpe.
     */
    public static void init() {
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
                        var gifData = IOTool.readGif(gif.toPath().toAbsolutePath());
                        if (gifData != null) {
                            ImageRenderer renderer = ImageAPI.renderer(gifData);
                            cache.put(gif.getName(), renderer);
                            System.out.println("[VctMedia] GIF pre-cargado: " + gif.getName());
                        }

                        // Pausa entre GIFs para no saturar la RAM
                        Thread.sleep(500);

                    } catch (Exception e) {
                        System.err.println("[VctMedia] Error pre-cargando " + gif.getName() + ": " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "VctMedia-GifPreCache");

        thread.setDaemon(true); // muere con el juego, no bloquea el cierre
        thread.start();
    }

    /**
     * ImageMedia llama esto. Si está en caché devuelve el renderer listo.
     * NO lo consume (no lo borra) para que pueda usarse varias veces seguidas.
     */
    public static ImageRenderer get(String filename) {
        return cache.get(filename);
    }

    public static boolean isReady(String filename) {
        return cache.containsKey(filename);
    }

    /**
     * Libera un GIF específico de VRAM + RAM cuando ya no lo necesites.
     */
    public static void evict(String filename) {
        ImageRenderer r = cache.remove(filename);
        if (r != null) {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    /**
     * Libera TODO al desconectarse del servidor.
     */
    public static void evictAll() {
        for (ImageRenderer r : cache.values()) {
            try { r.release(); } catch (Exception ignored) {}
        }
        cache.clear();
        initialized = false; // permite re-inicializar en la próxima sesión
    }
}