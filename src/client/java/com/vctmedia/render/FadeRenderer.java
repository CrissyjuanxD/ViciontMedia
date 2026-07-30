package com.vctmedia.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

public class FadeRenderer {
    public static void render(DrawContext context, MinecraftClient client, float alpha) {
        Window window = client.getWindow();
        float guiScale = (float) window.getScaleFactor();
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        context.getMatrices().push();
        context.getMatrices().scale(1.0f / guiScale, 1.0f / guiScale, 1.0f);

        float r = 0.0f, g = 0.0f, b = 0.0f;

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, 0, height, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, width, height, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, width, 0, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, 0, 0, 0).color(r, g, b, alpha);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        context.getMatrices().pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
