package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MediaOverlay {
    private static final float REFERENCE_HEIGHT = 1080.0f;

    private static DynamicTexture backingTexture;
    private static int lastVideoWidth = -1;
    private static int lastVideoHeight = -1;

    private static void ensureTexture(int width, int height) {
        if (width <= 0) width = 1;
        if (height <= 0) height = 1;

        if (backingTexture == null || lastVideoWidth != width || lastVideoHeight != height) {
            if (backingTexture != null) {
                backingTexture.close();
            }
            backingTexture = new DynamicTexture("vctmedia", width, height, false);
            lastVideoWidth = width;
            lastVideoHeight = height;
        }
    }

    private static void uploadFrame(int glId, int width, int height) {
        ensureTexture(width, height);

        try {
            ByteBuffer pixels = readGlTexturePixels(glId, width, height);
            if (pixels == null) return;

            NativeImage image = backingTexture.getPixels();
            long imagePointer = image.getPointer();

            org.lwjgl.system.MemoryUtil.memCopy(
                    org.lwjgl.system.MemoryUtil.memAddress(pixels),
                    imagePointer,
                    (long) width * height * 4
            );

            backingTexture.upload();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ByteBuffer readGlTexturePixels(int glId, int width, int height) {
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());

            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, glId);
            int previousAlignment = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
            org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, previousAlignment);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);

            buffer.flip();
            return buffer;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        var medias = MediaOrchestrator.getActiveList();

        if (!medias.isEmpty()) {
            float partialTick = tickCounter.getGameTimeDeltaPartialTick(true);
            Window window = Minecraft.getInstance().getWindow();

            float guiScale = (float) window.getGuiScale();
            int screenWidthPx = window.getWidth();
            int screenHeightPx = window.getHeight();

            for (AbstractMedia media : medias) {
                int textureId = media.getGlId(partialTick);
                if (textureId <= 0) continue;

                int mediaWidth = media.getWidth();
                int mediaHeight = media.getHeight();

                uploadFrame(textureId, mediaWidth, mediaHeight);

                if (backingTexture == null) continue;

                context.pose().pushMatrix();
                context.pose().scale(1.0f / guiScale, 1.0f / guiScale);

                float renderScale = screenHeightPx / REFERENCE_HEIGHT;
                context.pose().scale(renderScale, renderScale);

                float virtualScreenWidth = screenWidthPx / renderScale;
                float virtualScreenHeight = REFERENCE_HEIGHT;

                float x = 0, y = 0;
                float width, height;

                if (media.size <= 0) {
                    width = virtualScreenWidth;
                    height = virtualScreenHeight;
                } else {
                    width = media.size;
                    height = width * ((float) mediaHeight / mediaWidth);

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

                GpuTextureView view = backingTexture.getTextureView();
                GpuSampler sampler = backingTexture.getSampler();

                context.blit(view, sampler,
                        (int) x, (int) y, (int) width, (int) height,
                        0f, 0f, 1f, 1f);

                context.pose().popMatrix();
            }
        }

        VolumeManager.render(context);
    }

    public static void close() {
        if (backingTexture != null) {
            backingTexture.close();
            backingTexture = null;
        }
    }
}
