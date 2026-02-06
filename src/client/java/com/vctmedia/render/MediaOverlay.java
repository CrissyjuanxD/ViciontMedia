package com.vctmedia.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

public class MediaOverlay {
    // Usamos 1080p como altura de referencia estándar (Lógica tipo Owleaf/Badlion)
    private static final float REFERENCE_HEIGHT = 1080.0f;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        var medias = MediaOrchestrator.getActiveList();

        if (!medias.isEmpty()) {
            float partialTick = tickCounter.getTickDelta(true);
            Tessellator tessellator = Tessellator.getInstance();
            Window window = MinecraftClient.getInstance().getWindow();

            float guiScale = (float) window.getScaleFactor();
            float screenHeight = (float) window.getFramebufferHeight();

            float normalizationScale = screenHeight / REFERENCE_HEIGHT;

            for (TextureWrapper media : medias) {
                int textureId = media.getGlId(partialTick);
                if (textureId <= 0) continue;

                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                RenderSystem.setShaderTexture(0, textureId);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                context.getMatrices().push();

                // Deshacemos la escala de la GUI de Minecraft para trabajar en píxeles reales
                context.getMatrices().scale(1.0f / guiScale, 1.0f / guiScale, 1.0f);

                // Aplicamos nuestra escala normalizada basada en 1080p
                context.getMatrices().scale(normalizationScale, normalizationScale, 1.0f);

                int x, y, width, height;

                if (media.size <= 0) {
                    x = y = 0;
                    width = (int) (window.getFramebufferWidth() / normalizationScale);
                    height = (int) REFERENCE_HEIGHT;
                } else {
                    x = media.x;
                    y = media.y;
                    width = media.size;
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

        VolumeManager.render(context);
    }
}