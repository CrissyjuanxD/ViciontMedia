package com.vctmedia.mixin.client;

import com.vctmedia.render.AbstractMedia;
import com.vctmedia.util.MediaOrchestrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void hideChatDuringMedia(CallbackInfo ci) {
        boolean hideChat = false;

        for (AbstractMedia media : MediaOrchestrator.getActiveList()) {
            if (media.size <= 0 && media.opacity >= 100) {
                hideChat = true;
                break;
            }
        }

        if (hideChat) {
            if (!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) {
                ci.cancel();
            }
        }
    }
}
