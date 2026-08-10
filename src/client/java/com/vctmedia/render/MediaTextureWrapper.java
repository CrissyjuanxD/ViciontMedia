package com.vctmedia.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;

public class MediaTextureWrapper extends AbstractTexture {
    private int currentGlId = -1;
    private int currentWidth = -1;
    private int currentHeight = -1;

    public void updateTexture(int glId, int width, int height) {
        // Evitar regenerar objetos si no hay un cambio de frame/dimensiones
        if (this.currentGlId == glId && this.currentWidth == width && this.currentHeight == height && this.glTexture != null) {
            return;
        }

        this.currentGlId = glId;
        this.currentWidth = width;
        this.currentHeight = height;

        // Liberar la vista anterior de memoria antes de crear la nueva
        if (this.glTextureView != null) {
            this.glTextureView.close();
            this.glTextureView = null;
        }

        // IMPORTANTE: Dejamos el puntero en nulo, pero NO llamamos a close()
        // sobre la textura anterior, ya que WaterMedia controla su ciclo de vida real en la VRAM.
        this.glTexture = null;

        if (glId > 0 && width > 0 && height > 0) {
            // Constructor de 8 argumentos para 1.21.11:
            // usage (1 = SAMPLED), label, format, width, height, depthOrLayers (1), mipLevels (1), glId
            this.glTexture = new GlTexture(1, "vctmedia_" + glId, TextureFormat.RGBA8, width, height, 1, 1, glId) {
                @Override
                public void close() {
                    // Vacío intencionalmente para proteger la textura real controlada por WaterMedia
                }
            };

            // RenderSystem ahora usa el Device interno para crear la vista proyectada requerida
            this.glTextureView = RenderSystem.getDevice().createTextureView(this.glTexture);
        }
    }
}