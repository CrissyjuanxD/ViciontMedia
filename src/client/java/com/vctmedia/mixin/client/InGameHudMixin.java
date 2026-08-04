package com.vctmedia.mixin.client;

import com.vctmedia.render.MediaOverlay;
import com.vctmedia.render.TextOverlayRenderer;
import com.vctmedia.render.FadeRenderer;
import com.vctmedia.util.FadeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

	@Shadow private Text overlayMessage;
	@Shadow private int overlayRemaining;

	private boolean isVanillaOverlay = false;

	@ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
	private Text modifyActionbarText(Text message) {
		if (message != null) {
			String raw = message.getString();

			if (raw.startsWith("\u200B") || raw.startsWith("n!")) {
				this.isVanillaOverlay = true;

				if (raw.startsWith("n!")) {
					String clean = raw.startsWith("n! ") ? raw.substring(3) : raw.substring(2);
					return Text.literal(clean).setStyle(message.getStyle());
				}

				MutableText cleanText = Text.empty().setStyle(message.getStyle());

				for (Text sibling : message.getSiblings()) {
					cleanText.append(sibling);
				}

				return cleanText;
			}
		}

		this.isVanillaOverlay = false;
		return message;
	}

	private void renderEffectsAndOverlays(DrawContext context, RenderTickCounter tickCounter, MinecraftClient client) {
		MediaOverlay.render(context, tickCounter);
		TextOverlayRenderer.render(context, tickCounter);

		if (FadeManager.isFading) {
			float alpha = FadeManager.getFadeAlpha();
			if (alpha > 0.0f) {
				FadeRenderer.render(context, client, alpha);
			}
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void onRenderReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		renderEffectsAndOverlays(context, tickCounter, client);
	}

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void onRenderOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!client.options.hudHidden) {
			if (this.overlayRemaining > 0 && this.overlayMessage != null) {

				if (this.isVanillaOverlay) {
					return;
				}

				ci.cancel();

				float f = (float)this.overlayRemaining - tickCounter.getTickProgress(true);
				int alpha = (int)(f * 255.0F / 20.0F);
				if (alpha > 255) alpha = 255;

				if (alpha > 8) {
					context.getMatrices().pushMatrix();

					List<OrderedText> lines = client.textRenderer.wrapLines(this.overlayMessage, 10000);

					int fontHeight = client.textRenderer.fontHeight;
					int paddingX = 6;
					int paddingY = 4;
					int lineSpacing = 2;

					int maxWidth = 0;
					for (OrderedText line : lines) {
						int w = client.textRenderer.getWidth(line);
						if (w > maxWidth) maxWidth = w;
					}

					int totalHeight = (lines.size() * fontHeight) + ((lines.size() - 1) * lineSpacing);

					Window window = client.getWindow();
					int screenWidth = window.getScaledWidth();
					int screenHeight = window.getScaledHeight();

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
					for (OrderedText line : lines) {
						int lineWidth = client.textRenderer.getWidth(line);
						int lineX = (screenWidth - lineWidth) / 2;

						int textColor = 0xFFFFFF | (alpha << 24);

						context.drawTextWithShadow(client.textRenderer, line, lineX, currentY, textColor);
						currentY += fontHeight + lineSpacing;
					}

					context.getMatrices().popMatrix();
				}
			}
		}
	}
}
