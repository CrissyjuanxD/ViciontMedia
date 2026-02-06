package com.vctmedia.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;

public class VolumeManager {
    private static int volume = 70;
    private static long showUntil = 0;

    public static int getVolume() { return volume; }

    public static void setVolume(int vol) { volume = vol; }

    public static void changeVolume(int delta) {
        volume = Math.max(0, Math.min(100, volume + delta));
        showUntil = System.currentTimeMillis() + 2000;
    }

    public static void render(DrawContext context) {
        if (System.currentTimeMillis() < showUntil) {
            String text = "§fViciontMedia Volumen: §6" + volume + "%";

            MinecraftClient client = MinecraftClient.getInstance();
            Window window = client.getWindow();

            // Trabajamos en píxeles reales primero para posicionar
            float scaleFactor = (float) window.getScaleFactor();
            float screenWidth = window.getFramebufferWidth();
            float screenHeight = window.getFramebufferHeight();

            // Queremos el texto al 1.5x de tamaño
            float textScale = 1.5f;

            context.getMatrices().push();

            // 1. Resetear escala a píxeles reales (ignorando GUI Scale)
            context.getMatrices().scale(1.0f / scaleFactor, 1.0f / scaleFactor, 1.0f);

            // 2. Aplicar nuestra escala personalizada (1.5x)
            context.getMatrices().scale(textScale, textScale, 1.0f);

            float textWidth = client.textRenderer.getWidth(text);
            float textHeight = client.textRenderer.fontHeight;

            // Calculamos posición: (AnchoReal / EscalaTexto) - AnchoTexto - Margen
            float x = (screenWidth / textScale) - textWidth - 10;
            float y = (screenHeight / textScale) - textHeight - 10;

            context.drawTextWithShadow(client.textRenderer, text, (int)x, (int)y, 0xFFFFFF);

            context.getMatrices().pop();
        }
    }
}