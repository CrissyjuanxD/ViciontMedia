package com.vctmedia.mixin.client;

import com.vctmedia.util.VctShaderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void renderCustomShader(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {

        VctShaderManager.updateFadeAnim();

        if (VctShaderManager.isEnabled && VctShaderManager.currentShader != null) {
            Minecraft client = Minecraft.getInstance();
            VctShaderManager.currentShader.process(
                    ((GameRenderer) (Object) this).mainRenderTarget(),
                    GraphicsResourceAllocator.UNPOOLED
            );
        }
    }
}
