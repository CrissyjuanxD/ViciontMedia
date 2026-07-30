package com.vctmedia.util;

public class FadeManager {
    public static boolean isFading = false;
    public static long fadeStartMs = 0;
    public static long fadeEndFadeStartMs = -1;
    public static long fadeEndTime = 0;

    // Fade inicial (entrada del video): funde a negro, se mantiene 10s esperando que cargue, funde a claro
    public static final long FADE_IN = 1400;
    public static final long FADE_STAY = 10000;
    public static final long FADE_OUT = 1400;

    // Fade final (fin del video): funde a negro, pausa breve, funde a claro
    public static final long END_FADE_IN = 1400;
    public static final long END_FADE_STAY = 600;
    public static final long END_FADE_OUT = 1400;

    public static void startFade(long duration) {
        isFading = true;
        fadeStartMs = System.currentTimeMillis();
        fadeEndFadeStartMs = -1;
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

        // FASE INICIAL (Fade In -> Stay 10s -> Fade Out claro)
        long startFadeInEnd = fadeStartMs + FADE_IN;
        long startStayEnd = startFadeInEnd + FADE_STAY;
        long startFadeOutEnd = startStayEnd + FADE_OUT;

        if (now < startFadeInEnd) {
            return (float) (now - fadeStartMs) / FADE_IN;
        } else if (now < startStayEnd) {
            return 1.0f;
        } else if (now < startFadeOutEnd) {
            long elapsed = now - startStayEnd;
            return 1.0f - ((float) elapsed / FADE_OUT);
        }

        return 0.0f;
    }
}
