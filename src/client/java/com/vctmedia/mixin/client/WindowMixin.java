package com.vctmedia.mixin.client;

import com.vctmedia.util.ShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(at = @At("TAIL"), method = "onFramebufferSizeChanged")
    private void vctmedia$updateShaderSize(CallbackInfo ci) {
        ShaderManager.onResize();
    }
}
