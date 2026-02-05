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
        var medias = MediaOrchestrator.getActiveList();
        if (medias.isEmpty()) return;

        float partialTick = tickCounter.getTickDelta(true);
        Tessellator tessellator = Tessellator.getInstance();

        for (TextureWrapper media : medias) {
            int textureId = media.getGlId(partialTick);
            if (textureId <= 0) continue;

            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, textureId);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            context.getMatrices().push();

            int x, y, width, height;
            if (media.size <= 0) { // Fullscreen
                x = y = 0;
                width = context.getScaledWindowWidth();
                height = context.getScaledWindowHeight();
            } else {
                Window window = MinecraftClient.getInstance().getWindow();
                float scale = (float) window.getScaleFactor();
                context.getMatrices().scale(1.0f/scale, 1.0f/scale, 1.0f);
                x = (int)(media.x * scale);
                y = (int)(media.y * scale);
                width = (int)(media.size * scale);
                height = (int)(width * ((float)media.getHeight() / media.getWidth()));
            }

            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            buffer.vertex(matrix, x, y + height, 0).texture(0f, 1f);
            buffer.vertex(matrix, x + width, y + height, 0).texture(1f, 1f);
            buffer.vertex(matrix, x + width, y, 0).texture(1f, 0f);
            buffer.vertex(matrix, x, y, 0).texture(0f, 0f);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            context.getMatrices().pop();
        }
        RenderSystem.disableBlend();
    }
}