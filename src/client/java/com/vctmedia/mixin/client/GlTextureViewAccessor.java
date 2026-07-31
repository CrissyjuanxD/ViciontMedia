package com.vctmedia.mixin.client;

import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.GlTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTextureView.class)
public interface GlTextureViewAccessor {

    @Invoker("<init>")
    static GlTextureView vctmedia$invokeConstructor(GlTexture texture, int baseMipLevel, int mipLevels) {
        throw new AssertionError();
    }
}
