package com.vctmedia.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vctmedia.util.MediaOrchestrator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

public class MediaOverlay {
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        TextureWrapper media = MediaOrchestrator.getActive();
        if (media == null) return;

        float partialTick = tickCounter.getTickDelta(true);
        int textureId = media.getGlId(partialTick);
        if (textureId <= 0) return;

        // CONFIGURACIÓN ESTILO OWLEAF
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, textureId); // Forzamos el ID de WaterMedia
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        context.getMatrices().push();

        int x, y, width, height;
        if (MediaOrchestrator.renderSize <= 0) {
            x = y = 0;
            width = context.getScaledWindowWidth();
            height = context.getScaledWindowHeight();
        } else {
            Window window = MinecraftClient.getInstance().getWindow();
            float inv = (float) (1.0 / window.getScaleFactor());
            context.getMatrices().scale(inv, inv, 1.0f);
            x = MediaOrchestrator.posX;
            y = MediaOrchestrator.posY;
            width = MediaOrchestrator.renderSize;
            height = (int) (width * ((float) media.getHeight() / media.getWidth()));
        }

        // DIBUJADO MANUAL (Evita el cuadro morado/negro)
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        buffer.vertex(matrix, x, y + height, 0).texture(0f, 1f);
        buffer.vertex(matrix, x + width, y + height, 0).texture(1f, 1f);
        buffer.vertex(matrix, x + width, y, 0).texture(1f, 0f);
        buffer.vertex(matrix, x, y, 0).texture(0f, 0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        context.getMatrices().pop();
        RenderSystem.disableBlend();
    }
}