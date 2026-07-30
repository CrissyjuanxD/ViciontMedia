package com.vctmedia.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextOrchestrator {
    private static final Map<String, TextData> activeTexts = new ConcurrentHashMap<>();
    private static final Pattern COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|&([0-9a-fk-orA-FK-OR+\\-])");
    private static final Pattern SIZE_PATTERN = Pattern.compile("^(-?\\d+)%(.*)");

    public static class FormatState {
        public int color = 0xFFFFFF;
        public boolean bold, italic, underline, strike, obfuscated, pulsate;

        public FormatState copy() {
            FormatState s = new FormatState();
            s.color = this.color;
            s.bold = this.bold;
            s.italic = this.italic;
            s.underline = this.underline;
            s.strike = this.strike;
            s.obfuscated = this.obfuscated;
            s.pulsate = this.pulsate;
            return s;
        }

        public void reset() {
            color = 0xFFFFFF;
            bold = false; italic = false;
            underline = false; strike = false;
            obfuscated = false; pulsate = false;
        }
    }

    public static class AtomicText {
        public final String text;
        public final int color;
        public final boolean bold, italic, underline, strike, obfuscated, pulsate;

        public AtomicText(String text, FormatState state) {
            this.text = text;
            this.color = state.color;
            this.bold = state.bold;
            this.italic = state.italic;
            this.underline = state.underline;
            this.strike = state.strike;
            this.obfuscated = state.obfuscated;
            this.pulsate = state.pulsate;
        }
    }

    public static class TextSegment {
        public final List<AtomicText> atoms;
        public final int scale;
        public final Text baseText;

        public TextSegment(List<AtomicText> atoms, int scale) {
            this.atoms = atoms;
            this.scale = scale;

            MutableText mText = Text.empty();
            for (AtomicText atom : atoms) {
                Style style = Style.EMPTY.withColor(atom.color)
                        .withBold(atom.bold).withItalic(atom.italic)
                        .withUnderline(atom.underline).withStrikethrough(atom.strike)
                        .withObfuscated(atom.obfuscated);
                mText.append(Text.literal(atom.text).setStyle(style));
            }
            this.baseText = mText;
        }
    }

    public static class TextLine {
        public final List<TextSegment> segments = new ArrayList<>();
        public String alignment = "center";
        public float width;
        public float height;
    }

    public static class ParseResult {
        public final List<AtomicText> atoms;
        public final FormatState state;
        public ParseResult(List<AtomicText> atoms, FormatState state) {
            this.atoms = atoms;
            this.state = state;
        }
    }

    public static class TextData {
        public final String originalText;
        public final List<TextLine> lines = new ArrayList<>();
        public final int bgColor;
        public final boolean isTransparent;
        public final String pos;
        public long endTime;
        public long startTime;
        public final int globalSize;
        public final String animation;

        public TextData(String text, String bgColorStr, int durationSec, String pos, int globalSize, String animation) {
            this.originalText = text;
            this.pos = pos.toLowerCase();
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + (durationSec * 1000L);
            this.globalSize = globalSize;
            this.animation = animation != null ? animation.toLowerCase() : "default";

            String unescapedText = unescapeUnicode(text);
            String[] rawLines = unescapedText.replace("\\n", "\n").split("\n");

            FormatState currentState = new FormatState();
            int currentScale = 0;

            for (String rawLine : rawLines) {
                // AQUI ESTA LA MAGIA: Reseteamos el color y estilo al inicio de cada línea.
                currentState.reset();

                TextLine line = new TextLine();
                rawLine = rawLine.trim();

                if (rawLine.startsWith("[left]")) { line.alignment = "left"; rawLine = rawLine.substring(6).trim(); }
                else if (rawLine.startsWith("[center]")) { line.alignment = "center"; rawLine = rawLine.substring(8).trim(); }
                else if (rawLine.startsWith("[right]")) { line.alignment = "right"; rawLine = rawLine.substring(7).trim(); }
                else if (rawLine.startsWith("[justify]")) { line.alignment = "justify"; rawLine = rawLine.substring(9).trim(); }

                String[] tokens = rawLine.split(" ");

                for (String token : tokens) {
                    if (token.isEmpty()) continue;

                    Matcher m = SIZE_PATTERN.matcher(token);
                    if (m.matches()) {
                        currentScale = Integer.parseInt(m.group(1));
                        token = m.group(2);
                    }

                    if (!token.isEmpty()) {
                        ParseResult pr = parseColors(token, currentState);
                        currentState = pr.state;
                        line.segments.add(new TextSegment(pr.atoms, currentScale));
                    }
                }
                this.lines.add(line);
            }

            int parsedColor = 0;
            boolean transparent = false;
            try {
                String cleanHex = bgColorStr.startsWith("#") ? bgColorStr.substring(1) : bgColorStr;
                if (cleanHex.equalsIgnoreCase("none") || cleanHex.equalsIgnoreCase("transparent")) {
                    transparent = true;
                } else if (!cleanHex.isEmpty()) {
                    parsedColor = Integer.parseInt(cleanHex, 16);
                }
            } catch (Exception ignored) {}

            this.bgColor = parsedColor;
            this.isTransparent = transparent;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > endTime;
        }
    }

    private static String unescapeUnicode(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\\' && i + 1 < text.length() && text.charAt(i + 1) == 'u' && i + 5 < text.length()) {
                try {
                    int code = Integer.parseInt(text.substring(i + 2, i + 6), 16);
                    sb.append((char) code);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(text.charAt(i));
            i++;
        }
        return sb.toString();
    }

    private static ParseResult parseColors(String input, FormatState startState) {
        List<AtomicText> atoms = new ArrayList<>();
        FormatState state = startState.copy();

        Matcher matcher = COLOR_PATTERN.matcher(input);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                atoms.add(new AtomicText(input.substring(lastEnd, matcher.start()), state));
            }

            if (matcher.group(1) != null) {
                state.color = Integer.parseInt(matcher.group(1), 16);
            } else if (matcher.group(2) != null) {
                char code = matcher.group(2).toLowerCase().charAt(0);
                switch (code) {
                    case '+': state.pulsate = true; break;
                    case '-': state.pulsate = false; break;
                    case '0': state.color = 0x000000; break;
                    case '1': state.color = 0x0000AA; break;
                    case '2': state.color = 0x00AA00; break;
                    case '3': state.color = 0x00AAAA; break;
                    case '4': state.color = 0xAA0000; break;
                    case '5': state.color = 0xAA00AA; break;
                    case '6': state.color = 0xFFAA00; break;
                    case '7': state.color = 0xAAAAAA; break;
                    case '8': state.color = 0x555555; break;
                    case '9': state.color = 0x5555FF; break;
                    case 'a': state.color = 0x55FF55; break;
                    case 'b': state.color = 0x55FFFF; break;
                    case 'c': state.color = 0xFF5555; break;
                    case 'd': state.color = 0xFF55FF; break;
                    case 'e': state.color = 0xFFFF55; break;
                    case 'f': state.color = 0xFFFFFF; break;
                    case 'k': state.obfuscated = true; break;
                    case 'l': state.bold = true; break;
                    case 'm': state.strike = true; break;
                    case 'n': state.underline = true; break;
                    case 'o': state.italic = true; break;
                    case 'r': state.reset(); break;
                }
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            atoms.add(new AtomicText(input.substring(lastEnd), state));
        }

        return new ParseResult(atoms, state);
    }

    public static void addText(String bgColor, int durationSec, String pos, String text, int size, String animation, boolean sync) {
        String key = pos.toLowerCase();

        if (activeTexts.containsKey(key)) {
            TextData existing = activeTexts.get(key);
            TextData newData = new TextData(text, bgColor, durationSec, pos, size, animation);
            if (sync) {
                newData.startTime = existing.startTime;
            }
            activeTexts.put(key, newData);
        } else {
            activeTexts.put(key, new TextData(text, bgColor, durationSec, pos, size, animation));
        }
    }

    public static void removeTextByContent(String textToRemove) {
        activeTexts.values().removeIf(data -> data.originalText.equalsIgnoreCase(textToRemove));
    }

    public static List<TextData> getActiveTexts() {
        activeTexts.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return new ArrayList<>(activeTexts.values());
    }

    public static List<String> getActiveTextStrings() {
        List<String> list = new ArrayList<>();
        for (TextData data : getActiveTexts()) {
            list.add(data.originalText);
        }
        return list;
    }

    public static void clearAll() {
        activeTexts.clear();
    }
}