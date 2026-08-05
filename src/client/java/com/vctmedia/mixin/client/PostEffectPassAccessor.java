package com.vctmedia.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PostPass.class)
public interface PostEffectPassAccessor {

    @Accessor("customUniforms")
    @Mutable
    Map<String, GpuBuffer> getCustomUniforms();

    @Accessor("name")
    String getName();
}
