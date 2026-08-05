package com.vctmedia.render;

import com.vctmedia.util.TextOrchestrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

public class TextOverlayRenderer {
    private static final float TEXT_SCALE = 2.4f;

    public static int calculatePulsateColor(int baseColor, long now, int charIndex) {
        long offset = charIndex * 80L;
        long time = Math.max(0, now - offset);

        float cycle = (time % 1500L) / 1500.0f;

        float wave = (float) Math.sin(cycle * Math.PI);
        float intensity = (float) Math.pow(wave, 6);

        int r1 = (baseColor >> 16) & 0xFF;
        int g1 = (baseColor >> 8) & 0xFF;
        int b1 = baseColor & 0xFF;

        int r2 = 230;
        int g2 = 230;
        int b2 = 230;

        int r = (int) (r1 + (r2 - r1) * intensity);
        int g = (int) (g1 + (g2 - g1) * intensity);
        int b = (int) (b1 + (b2 - b1) * intensity);

        return (r << 16) | (g << 8) | b;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        var texts = TextOrchestrator.getActiveTexts();
        if (texts.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (TextOrchestrator.TextData data : texts) {
            renderData(context, data, now, -1, -1);
        }
    }

    public static void renderData(GuiGraphicsExtractor context, TextOrchestrator.TextData data, long now, float overrideX, float overrideY) {
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        Font font = client.font;

        float guiScale = (float) window.getGuiScale();
        int screenWidthPx = window.getWidth();
        int screenHeightPx = window.getHeight();

        long elapsed = now - data.startTime;
        long timeLeft = data.endTime - now;

        if (overrideX != -1 && overrideY != -1) {
            elapsed = 5000;
            timeLeft = 5000;
        }

        if (timeLeft <= 0 || elapsed < 0) return;

        float alphaFactor = 1.0f;
        float slideProgress = 1.0f;

        if (data.animation.equals("default")) {
            float animOut = Math.max(0.0f, Math.min(timeLeft, 500.0f) / 500.0f);
            alphaFactor = animOut;
        } else if (data.animation.equals("fade")) {
            float animIn = Math.max(0.0f, Math.min(elapsed, 1000.0f) / 1000.0f);
            float animOut = Math.max(0.0f, Math.min(timeLeft, 1000.0f) / 1000.0f);
            alphaFactor = Math.min(animIn, animOut);
        } else if (data.animation.equals("izquierda") || data.animation.equals("derecha") || data.animation.equals("arriba") || data.animation.equals("abajo")) {
            float animIn = Math.max(0.0f, Math.min(elapsed, 600.0f) / 600.0f);
            float animOut = Math.max(0.0f, Math.min(timeLeft, 500.0f) / 500.0f);

            if (animIn < 1.0f) {
                float t = animIn;
                slideProgress = 1.0f + 1.8f * (float)Math.pow(t - 1.0f, 3) + 1.2f * (float)Math.pow(t - 1.0f, 2);
            } else if (animOut < 1.0f) {
                float t = animOut;
                slideProgress = t * t * t;
            }
            alphaFactor = Math.min(animIn * 4.0f, animOut * 4.0f);
        }

        alphaFactor = Math.max(0.0f, Math.min(1.0f, alphaFactor));
        if (alphaFactor <= 0.01f) return;

        context.pose().pushMatrix();
        context.pose().scale(1.0f / guiScale, 1.0f / guiScale);

        float renderScale = screenHeightPx / 1080.0f;
        float baseScale = TEXT_SCALE;
        if (data.globalSize != 0) {
            baseScale += data.globalSize * 0.15f;
        }
        float finalScale = renderScale * Math.max(0.1f, baseScale);
        context.pose().scale(finalScale, finalScale);

        float virtualScreenWidth = screenWidthPx / finalScale;
        float virtualScreenHeight = screenHeightPx / finalScale;

        float maxLineWidth = 0;
        float totalHeight = 0;
        int padding = 12;
        int lineSpacing = 4;
        float spaceWidth = font.width(" ");

        for (TextOrchestrator.TextLine line : data.lines) {
            float lineWidth = 0;
            float lineMaxHeight = font.lineHeight;

            for (int i = 0; i < line.segments.size(); i++) {
                TextOrchestrator.TextSegment seg = line.segments.get(i);
                float segScale = 1.0f + (seg.scale * 0.01f);

                lineWidth += font.width(seg.baseText) * segScale;
                if (i < line.segments.size() - 1) {
                    lineWidth += spaceWidth * segScale;
                }
                if (font.lineHeight * segScale > lineMaxHeight) {
                    lineMaxHeight = font.lineHeight * segScale;
                }
            }
            line.width = lineWidth;
            line.height = lineMaxHeight;
            if (lineWidth > maxLineWidth) maxLineWidth = lineWidth;
            totalHeight += lineMaxHeight + lineSpacing;
        }
        totalHeight -= lineSpacing;

        float boxWidth = maxLineWidth + (padding * 2);
        float boxHeight = totalHeight + (padding * 2);

        float x = 0, y = 0;
        int margin = 5;

        if (overrideX != -1 && overrideY != -1) {
            x = overrideX;
            y = overrideY;
        } else {
            switch (data.pos) {
                case "topleft": x = margin; y = margin; break;
                case "topright": x = virtualScreenWidth - boxWidth - margin; y = margin; break;
                case "bottomleft": x = margin; y = virtualScreenHeight - boxHeight - margin; break;
                case "bottomright": x = virtualScreenWidth - boxWidth - margin; y = virtualScreenHeight - boxHeight - margin; break;
                case "center": x = (virtualScreenWidth - boxWidth) / 2.0f; y = (virtualScreenHeight - boxHeight) / 2.0f; break;
                default:
                    if (data.pos.contains(",")) {
                        String[] parts = data.pos.split(",");
                        try {
                            float percentX = Float.parseFloat(parts[0].trim());
                            float percentY = Float.parseFloat(parts[1].trim());
                            x = virtualScreenWidth * (percentX / 100.0f) - (boxWidth / 2.0f);
                            y = virtualScreenHeight * (percentY / 100.0f) - (boxHeight / 2.0f);
                        } catch (NumberFormatException ignored) {}
                    } else {
                        x = (virtualScreenWidth - boxWidth) / 2.0f; y = (virtualScreenHeight - boxHeight) / 2.0f;
                    }
                    break;
            }
        }

        float offsetX = 0;
        float offsetY = 0;

        if (data.animation.equals("izquierda")) {
            offsetX = -(x + boxWidth + 50) * (1.0f - slideProgress);
        } else if (data.animation.equals("derecha")) {
            offsetX = (virtualScreenWidth - x + 50) * (1.0f - slideProgress);
        } else if (data.animation.equals("arriba")) {
            offsetY = -(y + boxHeight + 50) * (1.0f - slideProgress);
        } else if (data.animation.equals("abajo")) {
            offsetY = (virtualScreenHeight - y + 50) * (1.0f - slideProgress);
        }

        context.pose().translate(x + offsetX, y + offsetY);

        if (!data.isTransparent) {
            int bgAlpha = (int)(140 * alphaFactor);
            int bgColorFinal = (bgAlpha << 24) | (data.bgColor & 0xFFFFFF);
            context.fill(net.minecraft.client.renderer.RenderPipelines.GUI, 0, 0, (int)boxWidth, (int)boxHeight, bgColorFinal);
        }

        int textAlpha = (int)(255 * alphaFactor);
        int textColorBase = (textAlpha << 24) | 0xFFFFFF;

        float currentY = padding;
        for (TextOrchestrator.TextLine line : data.lines) {
            float startX = padding;
            float extraGap = 0;

            if (line.alignment.equals("center")) {
                startX = padding + (maxLineWidth - line.width) / 2.0f;
            } else if (line.alignment.equals("right")) {
                startX = padding + (maxLineWidth - line.width);
            } else if (line.alignment.equals("justify") && line.segments.size() > 1) {
                extraGap = (maxLineWidth - line.width) / (line.segments.size() - 1);
            }

            float currentX = startX;
            for (int i = 0; i < line.segments.size(); i++) {
                TextOrchestrator.TextSegment seg = line.segments.get(i);
                float segScale = 1.0f + (seg.scale * 0.01f);

                context.pose().pushMatrix();
                float yOffset = currentY + (line.height - (font.lineHeight * segScale)) / 2.0f;
                context.pose().translate(currentX, yOffset);
                context.pose().scale(segScale, segScale);

                MutableComponent frameText = Component.empty();
                int charIndex = 0;

                for (TextOrchestrator.AtomicText atom : seg.atoms) {
                    if (atom.pulsate) {
                        for (int c = 0; c < atom.text.length(); c++) {
                            int renderColor = calculatePulsateColor(atom.color, now, charIndex);
                            Style style = Style.EMPTY.withColor(renderColor)
                                    .withBold(atom.bold).withItalic(atom.italic)
                                    .withUnderlined(atom.underline).withStrikethrough(atom.strike)
                                    .withObfuscated(atom.obfuscated);
                            frameText.append(Component.literal(String.valueOf(atom.text.charAt(c))).setStyle(style));
                            charIndex++;
                        }
                    } else {
                        Style style = Style.EMPTY.withColor(atom.color)
                                .withBold(atom.bold).withItalic(atom.italic)
                                .withUnderlined(atom.underline).withStrikethrough(atom.strike)
                                .withObfuscated(atom.obfuscated);
                        frameText.append(Component.literal(atom.text).setStyle(style));
                        charIndex += atom.text.length();
                    }
                }

                context.text(font, frameText, 0, 0, textColorBase, true);
                context.pose().popMatrix();

                currentX += font.width(seg.baseText) * segScale;
                if (i < line.segments.size() - 1) {
                    currentX += (spaceWidth * segScale) + extraGap;
                }
            }
            currentY += line.height + lineSpacing;
        }
        context.pose().popMatrix();
    }
}
