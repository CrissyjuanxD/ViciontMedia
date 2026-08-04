package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.vctmedia.mixin.client.DrawContextAccessor;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MediaOverlay {
    private static final float REFERENCE_HEIGHT = 1080.0f;

    private static NativeImageBackedTexture backingTexture;
    private static int lastVideoWidth = -1;
    private static int lastVideoHeight = -1;

    private static GpuSampler gpuSampler;

    private static void ensureTexture(int width, int height) {
        if (width <= 0) width = 1;
        if (height <= 0) height = 1;

        if (backingTexture == null || lastVideoWidth != width || lastVideoHeight != height) {
            if (backingTexture != null) {
                backingTexture.close();
                gpuSampler = null;
            }
            backingTexture = new NativeImageBackedTexture(width, height, false);
            backingTexture.setFilter(true, false);
            lastVideoWidth = width;
            lastVideoHeight = height;
        }
    }

    private static void uploadFrame(int glId, int width, int height) {
        ensureTexture(width, height);

        try {
            ByteBuffer pixels = readGlTexturePixels(glId, width, height);
            if (pixels == null) return;

            NativeImage image = backingTexture.getImage();
            long imagePointer = ((com.vctmedia.mixin.client.NativeImageAccessor) (Object) image).getPointer();

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

    private static GpuSampler getSampler(NativeImageBackedTexture texture) {
        if (gpuSampler == null) {
            gpuSampler = texture.getTextureView().createSampler(
                    com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR
            );
        }
        return gpuSampler;
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        var medias = MediaOrchestrator.getActiveList();

        if (!medias.isEmpty()) {
            float partialTick = tickCounter.getTickProgress(true);
            Window window = MinecraftClient.getInstance().getWindow();

            float guiScale = (float) window.getScaleFactor();
            int screenWidthPx = window.getFramebufferWidth();
            int screenHeightPx = window.getFramebufferHeight();

            for (AbstractMedia media : medias) {
                int textureId = media.getGlId(partialTick);
                if (textureId <= 0) continue;

                int mediaWidth = media.getWidth();
                int mediaHeight = media.getHeight();

                uploadFrame(textureId, mediaWidth, mediaHeight);

                if (backingTexture == null) continue;

                float alpha = media.opacity / 100.0f;
                int color = ((int)(alpha * 255.0f) & 0xFF) << 24 | 0xFFFFFF;

                context.getMatrices().pushMatrix();
                context.getMatrices().scale(1.0f / guiScale, 1.0f / guiScale);

                float renderScale = screenHeightPx / REFERENCE_HEIGHT;
                context.getMatrices().scale(renderScale, renderScale);

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

                RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
                GpuSampler sampler = getSampler(backingTexture);

                ((DrawContextAccessor) context).vctmedia$drawTexturedQuad(
                        pipeline, backingTexture.getTextureView(), sampler,
                        (int) x, (int) y, (int) (x + width), (int) (y + height),
                        0f, 0f, 1f, 1f, color
                );

                context.getMatrices().popMatrix();
            }
        }

        VolumeManager.render(context);
    }

    public static void close() {
        if (backingTexture != null) {
            backingTexture.close();
            backingTexture = null;
        }
        if (gpuSampler != null) {
            gpuSampler.close();
            gpuSampler = null;
        }
    }
}
