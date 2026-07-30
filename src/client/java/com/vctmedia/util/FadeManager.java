package com.vctmedia.util;

public class FadeManager {
    public static boolean isFading = false;
    public static long fadeStartMs = 0;
    public static long fadeEndFadeStartMs = -1;
    public static long fadeEndTime = 0;

    public static final long FADE_IN = 1400;
    public static final long FADE_STAY = 400;
    public static final long FADE_OUT = 1400;

    public static void startFade(long duration) {
        isFading = true;
        fadeStartMs = System.currentTimeMillis();
        // Ya no calculamos el final aquí. Dejamos que el TextureWrapper
        // orqueste su propio final para asegurar sincronía perfecta de microsegundos.
        fadeEndFadeStartMs = -1;
    }

    public static void triggerEndFadeNow() {
        if (isFading && fadeEndFadeStartMs == -1) {
            fadeEndFadeStartMs = System.currentTimeMillis();
            fadeEndTime = fadeEndFadeStartMs + FADE_IN + FADE_STAY + FADE_OUT;
        } else if (isFading && fadeEndFadeStartMs != -1) {
            long now = System.currentTimeMillis();
            if (now < fadeEndFadeStartMs) {
                fadeEndFadeStartMs = now;
                fadeEndTime = fadeEndFadeStartMs + FADE_IN + FADE_STAY + FADE_OUT;
            }
        }
    }

    public static float getFadeAlpha() {
        if (!isFading) return 0.0f;
        long now = System.currentTimeMillis();

        // FASE FINAL (Prioridad si el final se desencadenó)
        if (fadeEndFadeStartMs != -1 && now >= fadeEndFadeStartMs) {
            long endFadeInEnd = fadeEndFadeStartMs + FADE_IN;
            long endStayEnd = endFadeInEnd + FADE_STAY;
            long endFadeOutEnd = endStayEnd + FADE_OUT;

            if (now < endFadeInEnd) {
                return (float) (now - fadeEndFadeStartMs) / FADE_IN;
            } else if (now < endStayEnd) {
                return 1.0f;
            } else if (now < endFadeOutEnd) {
                long elapsed = now - endStayEnd;
                return 1.0f - ((float) elapsed / FADE_OUT);
            } else {
                isFading = false;
                return 0.0f;
            }
        }

        // FASE INICIAL (Fade In -> Stay -> Fade out claro)
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

        return 0.0f; // Medio del video
    }
}