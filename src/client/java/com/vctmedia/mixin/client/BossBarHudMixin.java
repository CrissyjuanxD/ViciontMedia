/*
package com.vctmedia.mixin.client;

import com.vctmedia.util.BossBarState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    @Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true)
    private void onRenderBossBar(DrawContext context, int x, int y, BossBar bossBar, CallbackInfo ci) {
        // Leemos la variable desde el nuevo State
        if (BossBarState.renderOnlyTimers) {
            String name = bossBar.getName().getString();
            boolean isTimer = bossBar.getColor() == BossBar.Color.WHITE && name.matches(".*\\d{2}:\\d{2}:\\d{2}.*");
            if (!isTimer) {
                ci.cancel();
            }
        }
    }
}*/
