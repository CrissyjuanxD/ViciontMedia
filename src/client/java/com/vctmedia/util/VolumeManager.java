package com.vctmedia.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.Window;

public class VolumeManager {
    private static int volume = 70;
    private static long showUntil = 0;

    public static int getVolume() { return volume; }

    public static void setVolume(int vol) { volume = vol; }

    public static void changeVolume(int delta) {
        volume = Math.max(0, Math.min(100, volume + delta));
        showUntil = System.currentTimeMillis() + 2000;
    }

    public static void render(GuiGraphicsExtractor context) {
        if (System.currentTimeMillis() < showUntil) {
            String text = "§fViciontMedia Volumen: §6" + volume + "%";

            Minecraft client = Minecraft.getInstance();
            Window window = client.getWindow();

            float scaleFactor = (float) window.getGuiScale();
            int screenWidthPx = window.getWidth();
            int screenHeightPx = window.getHeight();

            context.pose().pushMatrix();

            context.pose().scale(1.0f / scaleFactor, 1.0f / scaleFactor);

            float renderScale = screenHeightPx / 1080.0f;

            float finalScale = renderScale * 2.2f;
            context.pose().scale(finalScale, finalScale);

            float virtualScreenWidth = screenWidthPx / finalScale;
            float virtualScreenHeight = screenHeightPx / finalScale;

            float textWidth = client.font.width(text);
            float textHeight = client.font.lineHeight;

            float x = virtualScreenWidth - textWidth - 10;
            float y = virtualScreenHeight - textHeight - 10;

            context.text(client.font, text, (int)x, (int)y, 0xFFFFFF, true);

            context.pose().popMatrix();
        }
    }
}
