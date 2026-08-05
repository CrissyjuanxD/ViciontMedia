package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.Window;

public class FadeRenderer {
    public static void render(GuiGraphicsExtractor context, Minecraft client, float alpha) {
        Window window = client.getWindow();
        float guiScale = (float) window.getGuiScale();
        int width = window.getWidth();
        int height = window.getHeight();

        context.pose().pushMatrix();
        context.pose().scale(1.0f / guiScale, 1.0f / guiScale);

        int colorAlpha = (int)(alpha * 255.0f) & 0xFF;
        int color = (colorAlpha << 24);

        RenderPipeline pipeline = RenderPipelines.GUI;
        context.fill(pipeline, 0, 0, width, height, color);

        context.pose().popMatrix();
    }
}
