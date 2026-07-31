package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.util.Window;

public class FadeRenderer {
    public static void render(DrawContext context, MinecraftClient client, float alpha) {
        Window window = client.getWindow();
        float guiScale = (float) window.getScaleFactor();
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(1.0f / guiScale, 1.0f / guiScale);

        int colorAlpha = (int)(alpha * 255.0f) & 0xFF;
        int color = (colorAlpha << 24);

        RenderPipeline pipeline = RenderPipelines.GUI;
        context.fill(pipeline, 0, 0, width, height, color);

        context.getMatrices().popMatrix();
    }
}
