package com.kntrel.mc.runical.bukkit;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;

public final class ComponentMarkupCompiler {

    private ComponentMarkupCompiler() {}

    public static BaseComponent compile(String input) {
        Objects.requireNonNull(input, "input");

        TextComponent root = new TextComponent("");
        if (input.isEmpty()) {
            return root;
        }

        Deque<MarkupTag> openTags = new ArrayDeque<>();
        StringBuilder textBuffer = new StringBuilder(input.length());
        int cursor = 0;

        while (cursor < input.length()) {
            int tagStart = input.indexOf('<', cursor);
            if (tagStart < 0) {
                textBuffer.append(input, cursor, input.length());
                break;
            }

            textBuffer.append(input, cursor, tagStart);

            int tagEnd = input.indexOf('>', tagStart + 1);
            if (tagEnd < 0) {
                textBuffer.append(input, tagStart, input.length());
                break;
            }

            if (!applyToken(input.substring(tagStart + 1, tagEnd), openTags, root, textBuffer)) {
                textBuffer.append(input, tagStart, tagEnd + 1);
            }

            cursor = tagEnd + 1;
        }

        appendText(root, textBuffer, openTags);
        return root;
    }

    private static boolean applyToken(String rawToken, Deque<MarkupTag> openTags, TextComponent root, StringBuilder textBuffer) {
        TagToken token = parseToken(rawToken);
        if (token == null) {
            return false;
        }

        switch (token.type()) {
            case OPEN -> {
                appendText(root, textBuffer, openTags);
                openTags.addLast(token.tag());
                return true;
            }
            case CLOSE_LAST -> {
                if (openTags.isEmpty()) {
                    return false;
                }
                appendText(root, textBuffer, openTags);
                openTags.removeLast();
                return true;
            }
            case CLOSE_SPECIFIC -> {
                if (!containsMatchingTag(openTags, token.tag().kind())) {
                    return false;
                }
                appendText(root, textBuffer, openTags);
                closeMatchingTag(openTags, token.tag().kind());
                return true;
            }
            default -> throw new IllegalStateException("Unhandled token type: " + token.type());
        }
    }

    private static TagToken parseToken(String rawToken) {
        String token = rawToken.trim();
        if (token.isEmpty()) {
            return null;
        }

        if (token.equals("/")) {
            return new TagToken(TokenType.CLOSE_LAST, null);
        }

        if (token.startsWith("/")) {
            TagKind kind = parseClosingTagKind(token.substring(1).trim().toLowerCase(Locale.ROOT));
            return kind == null ? null : new TagToken(TokenType.CLOSE_SPECIFIC, MarkupTag.of(kind));
        }

        TagKind formattingKind = parseFormattingTagKind(token.toLowerCase(Locale.ROOT));
        if (formattingKind != null) {
            return new TagToken(TokenType.OPEN, MarkupTag.of(formattingKind));
        }

        String lowered = token.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("c:")) {
            return null;
        }

        String colorSpec = token.substring(token.indexOf(':') + 1).trim();
        ChatColor color = tryParseColor(colorSpec);
        return color == null ? null : new TagToken(TokenType.OPEN, MarkupTag.color(color));
    }

    private static TagKind parseFormattingTagKind(String token) {
        return switch (token) {
            case "b" -> TagKind.BOLD;
            case "i" -> TagKind.ITALIC;
            case "s" -> TagKind.STRIKETHROUGH;
            case "u" -> TagKind.UNDERLINED;
            case "o" -> TagKind.OBFUSCATED;
            default -> null;
        };
    }

    private static TagKind parseClosingTagKind(String token) {
        if ("c".equals(token)) {
            return TagKind.COLOR;
        }
        return parseFormattingTagKind(token);
    }

    private static ChatColor tryParseColor(String colorSpec) {
        if (colorSpec.isBlank()) {
            return null;
        }

        String normalizedSpec = colorSpec.startsWith("#")
                ? "#" + colorSpec.substring(1).toUpperCase(Locale.ROOT)
                : colorSpec.toLowerCase(Locale.ROOT);

        try {
            return ChatColor.of(normalizedSpec);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean containsMatchingTag(Deque<MarkupTag> openTags, TagKind kind) {
        for (MarkupTag openTag : openTags) {
            if (openTag.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static void closeMatchingTag(Deque<MarkupTag> openTags, TagKind kind) {
        while (!openTags.isEmpty()) {
            MarkupTag tag = openTags.removeLast();
            if (tag.kind() == kind) {
                return;
            }
        }
    }

    private static void appendText(TextComponent root, StringBuilder textBuffer, Deque<MarkupTag> openTags) {
        if (textBuffer.length() == 0) {
            return;
        }

        TextComponent component = new TextComponent(textBuffer.toString());
        applyFormatting(component, openTags);
        root.addExtra(component);
        textBuffer.setLength(0);
    }

    private static void applyFormatting(TextComponent component, Deque<MarkupTag> openTags) {
        for (MarkupTag tag : openTags) {
            switch (tag.kind()) {
                case BOLD -> component.setBold(true);
                case ITALIC -> component.setItalic(true);
                case STRIKETHROUGH -> component.setStrikethrough(true);
                case UNDERLINED -> component.setUnderlined(true);
                case OBFUSCATED -> component.setObfuscated(true);
                case COLOR -> component.setColor(tag.color());
            }
        }
    }

    private enum TokenType {
        OPEN,
        CLOSE_LAST,
        CLOSE_SPECIFIC
    }

    private enum TagKind {
        BOLD,
        ITALIC,
        STRIKETHROUGH,
        UNDERLINED,
        OBFUSCATED,
        COLOR
    }

    private record TagToken(TokenType type, MarkupTag tag) {
    }

    private record MarkupTag(TagKind kind, ChatColor color) {

        static MarkupTag of(TagKind kind) {
            return new MarkupTag(kind, null);
        }

        static MarkupTag color(ChatColor color) {
            return new MarkupTag(TagKind.COLOR, Objects.requireNonNull(color, "color"));
        }
    }
}
