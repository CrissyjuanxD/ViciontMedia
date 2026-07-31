package com.vctmedia.mixin.client;

import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTexture.class)
public interface GlTextureAccessor {

    @Invoker("<init>")
    static GlTexture vctmedia$invokeConstructor(int glId, String label, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels, int usage) {
        throw new AssertionError();
    }
}
