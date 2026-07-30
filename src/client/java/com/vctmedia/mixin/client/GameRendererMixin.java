package com.vctmedia.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vctmedia.util.ShaderManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
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
            // 1. Limpiamos el color base para evitar el efecto "encendido"
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();

            // 2. Renderizamos el shader
            ShaderManager.currentShader.render(tickCounter.getTickDelta(true));

            // 3. RESTAURAMOS el estado de OpenGL para no romper la UI del juego
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        }
    }
}