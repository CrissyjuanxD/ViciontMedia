package com.vctmedia.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PostEffectPass.class)
public interface PostEffectPassAccessor {

    @Accessor("uniformBuffers")
    @Mutable
    Map<String, GpuBuffer> getUniformBuffers();

    @Accessor("id")
    String getId();
}
