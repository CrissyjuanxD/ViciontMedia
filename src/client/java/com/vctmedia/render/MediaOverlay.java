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
    private static final float REFERENCE_HEIGHT = 1080.0f;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        var medias = MediaOrchestrator.getActiveList();

        if (!medias.isEmpty()) {
            float partialTick = tickCounter.getTickDelta(true);
            Tessellator tessellator = Tessellator.getInstance();
            Window window = MinecraftClient.getInstance().getWindow();

            float guiScale = (float) window.getScaleFactor();
            int screenWidthPx = window.getFramebufferWidth();
            int screenHeightPx = window.getFramebufferHeight();

            for (AbstractMedia media : medias) {
                int textureId = media.getGlId(partialTick);
                if (textureId <= 0) continue;

                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                RenderSystem.setShaderTexture(0, textureId);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);

                float alpha = media.opacity / 100.0f;
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

                context.getMatrices().push();
                context.getMatrices().scale(1.0f / guiScale, 1.0f / guiScale, 1.0f);

                float renderScale = screenHeightPx / REFERENCE_HEIGHT;
                context.getMatrices().scale(renderScale, renderScale, 1.0f);

                float virtualScreenWidth = screenWidthPx / renderScale;
                float virtualScreenHeight = REFERENCE_HEIGHT;

                float x = 0, y = 0;
                float width, height;

                if (media.size <= 0) {
                    width = virtualScreenWidth;
                    height = virtualScreenHeight;
                } else {
                    width = media.size;
                    height = width * ((float) media.getHeight() / media.getWidth());

                    String pos = media.pos != null ? media.pos.toLowerCase() : "center";

                    switch (pos) {
                        case "topleft":
                            x = 0; y = 0; break;
                        case "top":
                        case "topcenter":
                            x = (virtualScreenWidth - width) / 2.0f; y = 0; break;
                        case "topright":
                            x = virtualScreenWidth - width; y = 0; break;
                        case "left":
                        case "centerleft":
                            x = 0; y = (virtualScreenHeight - height) / 2.0f; break;
                        case "center":
                            x = (virtualScreenWidth - width) / 2.0f; y = (virtualScreenHeight - height) / 2.0f; break;
                        case "right":
                        case "centerright":
                            x = virtualScreenWidth - width; y = (virtualScreenHeight - height) / 2.0f; break;
                        case "bottomleft":
                            x = 0; y = virtualScreenHeight - height; break;
                        case "bottom":
                        case "bottomcenter":
                            x = (virtualScreenWidth - width) / 2.0f; y = virtualScreenHeight - height; break;
                        case "bottomright":
                            x = virtualScreenWidth - width; y = virtualScreenHeight - height; break;
                        default:
                            if (pos.contains(",")) {
                                String[] parts = pos.split(",");
                                try {
                                    float percentX = Float.parseFloat(parts[0].trim());
                                    float percentY = Float.parseFloat(parts[1].trim());
                                    float basePx = virtualScreenWidth * (percentX / 100.0f);
                                    float basePy = virtualScreenHeight * (percentY / 100.0f);
                                    x = basePx - (width / 2.0f);
                                    y = basePy - (height / 2.0f);
                                } catch (NumberFormatException ignored) {}
                            }
                            break;
                    }
                }

                Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
                BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

                buffer.vertex(matrix, x, y + height, 0).texture(0f, 1f);
                buffer.vertex(matrix, x + width, y + height, 0).texture(1f, 1f);
                buffer.vertex(matrix, x + width, y, 0).texture(1f, 0f);
                buffer.vertex(matrix, x, y, 0).texture(0f, 0f);

                BufferRenderer.drawWithGlobalProgram(buffer.end());
                context.getMatrices().pop();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        VolumeManager.render(context);
    }
}