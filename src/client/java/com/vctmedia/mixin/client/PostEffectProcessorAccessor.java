package com.vctmedia.mixin.client;

import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Provides access to PostEffectProcessor's private "passes" field.
 * Kept for potential future use, though uniform updates are no longer
 * possible via getProgram().getUniformByName() in 1.21.11.
 */
@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
    @Accessor("passes")
    List<PostEffectPass> getPasses();
}
