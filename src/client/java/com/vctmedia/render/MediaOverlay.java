package com.vctmedia.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.vctmedia.mixin.client.GlTextureAccessor;
import com.vctmedia.mixin.client.GlTextureViewAccessor;
import com.vctmedia.util.MediaOrchestrator;
import com.vctmedia.util.VolumeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlSampler;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.GlTextureView;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.Window;

import java.util.OptionalDouble;

public class MediaOverlay {
    private static final float REFERENCE_HEIGHT = 1080.0f;
    private static GlSampler sampler;
    private static GlTexture cachedTexture;
    private static GlTextureView cachedTextureView;
    private static int cachedGlId = -1;

    private static GlSampler getSampler() {
        if (sampler == null) {
            sampler = new GlSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 0, OptionalDouble.empty());
        }
        return sampler;
    }

    private static GlTextureView getTextureView(int glId, int width, int height) {
        if (glId == cachedGlId && cachedTextureView != null && !cachedTextureView.isClosed()) {
            return cachedTextureView;
        }

        if (cachedTextureView != null) {
            cachedTextureView.close();
            cachedTexture = null;
            cachedTextureView = null;
        }

        if (width <= 0) width = 1;
        if (height <= 0) height = 1;

        cachedTexture = GlTextureAccessor.vctmedia$invokeConstructor(glId, "vctmedia_video",
                TextureFormat.RGBA8, width, height, 1, 1, GpuTexture.USAGE_TEXTURE_BINDING);
        cachedTextureView = GlTextureViewAccessor.vctmedia$invokeConstructor(cachedTexture, 0, 1);
        cachedGlId = glId;

        return cachedTextureView;
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

                GlTextureView textureView = getTextureView(textureId, media.getWidth(), media.getHeight());
                GpuSampler gpuSampler = getSampler();
                TextureSetup textureSetup = TextureSetup.of(textureView, gpuSampler);
                RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

                context.fill(pipeline, textureSetup, (int) x, (int) y, (int) (x + width), (int) (y + height));

                context.getMatrices().popMatrix();
            }
        }

        VolumeManager.render(context);
    }
}
