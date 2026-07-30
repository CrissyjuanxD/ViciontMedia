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

            float scaleFactor = (float) window.getScaleFactor();
            int screenWidthPx = window.getFramebufferWidth();
            int screenHeightPx = window.getFramebufferHeight();

            context.getMatrices().push();

            context.getMatrices().scale(1.0f / scaleFactor, 1.0f / scaleFactor, 1.0f);

            float renderScale = screenHeightPx / 1080.0f;

            float finalScale = renderScale * 2.2f;
            context.getMatrices().scale(finalScale, finalScale, 1.0f);

            float virtualScreenWidth = screenWidthPx / finalScale;
            float virtualScreenHeight = screenHeightPx / finalScale;

            float textWidth = client.textRenderer.getWidth(text);
            float textHeight = client.textRenderer.fontHeight;

            float x = virtualScreenWidth - textWidth - 10;
            float y = virtualScreenHeight - textHeight - 10;

            context.drawTextWithShadow(client.textRenderer, text, (int)x, (int)y, 0xFFFFFF);

            context.getMatrices().pop();
        }
    }
}