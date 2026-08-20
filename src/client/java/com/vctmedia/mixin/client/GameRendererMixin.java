package com.vctmedia.mixin.client;

import com.vctmedia.util.GameRendererPoolAccessor;
import com.vctmedia.util.ShaderManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.client.util.memory.ObjectPool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements GameRendererPoolAccessor {

    @Final
    @Shadow
    private ObjectPool pool;

    @Override
    public ObjectAllocator getPool() {
        return this.pool;
    }

    @Inject(
            method = "renderWorld",
            at = @At("RETURN")
    )
    private void vctmedia$renderCustomShader(RenderTickCounter tickCounter, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        ShaderManager.render(renderer);
    }
}
