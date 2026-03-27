package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.placeholder.Placeholder;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.TranslationJob;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;

final class TranslatorNode implements Translator {

    private final Runical root_;
    private final String path_;

    TranslatorNode(Runical root, String path) {
        this.root_ = Objects.requireNonNull(root, "root");
        this.path_ = Objects.requireNonNull(path, "path");
    }

    @Override
    public String getPath() {
        return this.path_;
    }

    @Override
    public Runical getRoot() {
        return this.root_;
    }

    @Override
    public Translator getChild(String segment) {
        return this.root_.childTranslator(qualifyPath(this.path_, segment));
    }

    @Override
    public TranslationJob translate(String locale, String key, Placeholder... args) {
        return this.root_.translate(locale, qualifyKey(key), args);
    }

    @Override
    public PlayerTranslationJob translate(Player player, String key, Placeholder... args) {
        return this.root_.translate(player, qualifyKey(key), args);
    }

    @Override
    public String formatList(Player player, Collection<?> items, ListStyle style) {
        return this.root_.formatList(player, items, style);
    }

    private String qualifyKey(String key) {
        String normalizedKey = requireKey(key);
        return this.path_ + "." + normalizedKey;
    }

    private static String qualifyPath(String path, String segment) {
        return path + "." + requireSegment(segment);
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Translation key must not be blank.");
        }
        return key;
    }

    private static String requireSegment(String segment) {
        Objects.requireNonNull(segment, "segment");
        String normalizedSegment = segment.trim();
        if (normalizedSegment.isBlank()) {
            throw new IllegalArgumentException("Translator child segment must not be blank.");
        }
        if (normalizedSegment.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Translator child segment must not contain dots.");
        }
        return normalizedSegment;
    }
}
