package com.vctmedia.mixin.client;

import com.vctmedia.util.ShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;updateWorldIcon()V"))
    private void renderCustomShader(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {

        ShaderManager.updateFadeAnim();

        if (ShaderManager.isEnabled && ShaderManager.currentShader != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            ObjectAllocator allocator = ObjectAllocator.TRIVIAL;
                ShaderManager.currentShader.render(client.getFramebuffer(), allocator);
        }
    }
}
