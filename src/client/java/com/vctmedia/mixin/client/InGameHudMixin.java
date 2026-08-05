package com.vctmedia.mixin.client;

import com.vctmedia.render.MediaOverlay;
import com.vctmedia.render.TextOverlayRenderer;
import com.vctmedia.render.FadeRenderer;
import com.vctmedia.util.FadeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Hud.class)
public abstract class InGameHudMixin {

    @Shadow private Component overlayMessageString;
    @Shadow private int overlayMessageTime;

    private boolean isVanillaOverlay = false;

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component modifyActionbarText(Component message) {
        if (message != null) {
            String raw = message.getString();

            if (raw.startsWith("\u200B") || raw.startsWith("n!")) {
                this.isVanillaOverlay = true;

                if (raw.startsWith("n!")) {
                    String clean = raw.startsWith("n! ") ? raw.substring(3) : raw.substring(2);
                    return Component.literal(clean).setStyle(message.getStyle());
                }

                MutableComponent cleanText = Component.empty().setStyle(message.getStyle());

                for (Component sibling : message.getSiblings()) {
                    cleanText.append(sibling);
                }

                return cleanText;
            }
        }

        this.isVanillaOverlay = false;
        return message;
    }

    private void renderEffectsAndOverlays(GuiGraphicsExtractor context, DeltaTracker tickCounter, Minecraft client) {
        MediaOverlay.render(context, tickCounter);
        TextOverlayRenderer.render(context, tickCounter);

        if (FadeManager.isFading) {
            float alpha = FadeManager.getFadeAlpha();
            if (alpha > 0.0f) {
                FadeRenderer.render(context, client, alpha);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        renderEffectsAndOverlays(context, tickCounter, client);
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlayMessage(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        if (this.overlayMessageTime > 0 && this.overlayMessageString != null) {

            if (this.isVanillaOverlay) {
                return;
            }

            ci.cancel();

            float f = (float)this.overlayMessageTime - tickCounter.getGameTimeDeltaPartialTick(true);
            int alpha = (int)(f * 255.0F / 20.0F);
            if (alpha > 255) alpha = 255;

            if (alpha > 8) {
                context.pose().pushMatrix();

                List<FormattedText> lines = client.font.getSplitter().splitLines(this.overlayMessageString, 10000, Style.EMPTY);

                int fontHeight = client.font.lineHeight;
                int paddingX = 6;
                int paddingY = 4;
                int lineSpacing = 2;

                int maxWidth = 0;
                for (FormattedText line : lines) {
                    int w = client.font.width(line);
                    if (w > maxWidth) maxWidth = w;
                }

                int totalHeight = (lines.size() * fontHeight) + ((lines.size() - 1) * lineSpacing);

                Window window = client.getWindow();
                int screenWidth = window.getGuiScaledWidth();
                int screenHeight = window.getGuiScaledHeight();

                int startY = screenHeight - 68 - totalHeight;
                int startX = (screenWidth - maxWidth) / 2;

                int bgAlpha = Math.min(50, alpha);
                int bgColor = (bgAlpha << 24) | 0x000000;

                int boxX1 = startX - paddingX;
                int boxY1 = startY - paddingY;
                int boxX2 = startX + maxWidth + paddingX;
                int boxY2 = startY + totalHeight + paddingY;

                context.fill(RenderPipelines.GUI, boxX1 + 2, boxY1, boxX2 - 2, boxY2, bgColor);
                context.fill(RenderPipelines.GUI, boxX1 + 1, boxY1 + 1, boxX2 - 1, boxY2 - 1, bgColor);
                context.fill(RenderPipelines.GUI, boxX1, boxY1 + 2, boxX2, boxY2 - 2, bgColor);

                int currentY = startY;
                for (FormattedText line : lines) {
                    int lineWidth = client.font.width(line);
                    int lineX = (screenWidth - lineWidth) / 2;

                    int textColor = 0xFFFFFF | (alpha << 24);

                    if (line instanceof Component) {
                        context.text(client.font, (Component) line, lineX, currentY, textColor, true);
                    } else {
                        MutableComponent plain = Component.literal(line.getString());
                        context.text(client.font, plain, lineX, currentY, textColor, true);
                    }
                    currentY += fontHeight + lineSpacing;
                }

                context.pose().popMatrix();
            }
        }
    }
}
