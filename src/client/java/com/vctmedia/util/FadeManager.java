package com.vctmedia.util;

public class FadeManager {
    public static boolean isFading = false;
    public static long fadeStartMs = 0;
    public static long fadeEndFadeStartMs = -1;
    public static long fadeEndTime = 0;

    // Fade inicial: funde a negro rapidamente, se mantiene negro hasta que el video aparezca
    // El fade-out (de negro a claro) se desencadena dinamicamente cuando el video esta listo
    public static final long FADE_IN = 1200;
    public static final long FADE_OUT = 1200;

    // Fade final (fin del video): funde a negro, pausa breve, funde a claro
    public static final long END_FADE_IN = 1200;
    public static final long END_FADE_STAY = 400;
    public static final long END_FADE_OUT = 1200;

    // Marca el momento en que el video aparecio — desencadena el fade-out inmediatamente
    private static long videoReadyMs = -1;

    public static void startFade(long duration) {
        isFading = true;
        fadeStartMs = System.currentTimeMillis();
        fadeEndFadeStartMs = -1;
        videoReadyMs = -1;
    }

    // Llamado por los media cuando detectan que el video ya tiene textura
    public static void notifyVideoReady() {
        if (isFading && videoReadyMs == -1) {
            videoReadyMs = System.currentTimeMillis();
        }
    }

    public static void triggerEndFadeNow() {
        long endTotalDuration = END_FADE_IN + END_FADE_STAY + END_FADE_OUT;
        if (isFading && fadeEndFadeStartMs == -1) {
            fadeEndFadeStartMs = System.currentTimeMillis();
            fadeEndTime = fadeEndFadeStartMs + endTotalDuration;
        } else if (isFading && fadeEndFadeStartMs != -1) {
            long now = System.currentTimeMillis();
            if (now < fadeEndFadeStartMs) {
                fadeEndFadeStartMs = now;
                fadeEndTime = fadeEndFadeStartMs + endTotalDuration;
            }
        }
    }

    public static float getFadeAlpha() {
        if (!isFading) return 0.0f;
        long now = System.currentTimeMillis();

        // FASE FINAL (Prioridad si el final se desencadenó)
        if (fadeEndFadeStartMs != -1 && now >= fadeEndFadeStartMs) {
            long endFadeInEnd = fadeEndFadeStartMs + END_FADE_IN;
            long endStayEnd = endFadeInEnd + END_FADE_STAY;
            long endFadeOutEnd = endStayEnd + END_FADE_OUT;

            if (now < endFadeInEnd) {
                return (float) (now - fadeEndFadeStartMs) / END_FADE_IN;
            } else if (now < endStayEnd) {
                return 1.0f;
            } else if (now < endFadeOutEnd) {
                long elapsed = now - endStayEnd;
                return 1.0f - ((float) elapsed / END_FADE_OUT);
            } else {
                isFading = false;
                return 0.0f;
            }
        }

        // FASE INICIAL: Fade In (a negro) -> Stay negro -> Fade Out (a claro)
        // El fade-out empieza cuando notifyVideoReady() es llamado
        long startFadeInEnd = fadeStartMs + FADE_IN;

        if (now < startFadeInEnd) {
            // Fase 1: fundiendo a negro
            return (float) (now - fadeStartMs) / FADE_IN;
        }

        // Fase 2: negro completo — esperando que el video aparezca
        if (videoReadyMs == -1) {
            // El video todavia no aparece. Timeout de seguridad: 15s max en negro
            if (now - startFadeInEnd > 15000) {
                videoReadyMs = now;
            }
            return 1.0f;
        }

        // Fase 3: el video aparecio — fundir de negro a claro rapidamente
        long fadeOutEnd = videoReadyMs + FADE_OUT;
        if (now < fadeOutEnd) {
            long elapsed = now - videoReadyMs;
            return 1.0f - ((float) elapsed / FADE_OUT);
        }

        return 0.0f;
    }
}
