package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class TranslatorNode implements Translator {

    private final Runical root;
    private final String path;


    TranslatorNode(Runical root, String path) {
        this.root = Objects.requireNonNull(root, "root");
        this.path = Objects.requireNonNull(path, "path");
    }


    @Override
    public String getPath_() {
        return this.path;
    }

    @Override
    public Translator getChild(String segment) {
        return this.root.childTranslator(qualifyPath(this.path, segment));
    }

    @Override
    public String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args) {
        return this.root.translateOrDefault(locale, qualifyKey(key), defaultValue, args);
    }

    @Override
    public CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args) {
        return this.root.translateOrDefaultAsync(locale, qualifyKey(key), defaultValue, args);
    }

    @Override
    public ResolvedTranslation resolve(String locale, String key, Placeholder... args) {
        return this.root.resolve(locale, qualifyKey(key), args);
    }

    @Override
    public CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
        return this.root.resolveAsync(locale, qualifyKey(key), args);
    }

    @Override
    public String translateOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        return this.root.translateOrDefault(player, qualifyKey(key), defaultValue, args);
    }

    @Override
    public CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue, Placeholder... args) {
        return this.root.translateOrDefaultAsync(player, qualifyKey(key), defaultValue, args);
    }

    @Override
    public ResolvedTranslation resolve(Player player, String key, Placeholder... args) {
        return this.root.resolve(player, qualifyKey(key), args);
    }

    @Override
    public CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key, Placeholder... args) {
        return this.root.resolveAsync(player, qualifyKey(key), args);
    }

    @Override
    public String formatList(Player player, Collection<?> items, ListStyle style) {
        return this.root.formatList(player, items, style);
    }

    @Override
    public CompletableFuture<Boolean> sendTranslation(Player player, String key, Placeholder... args) {
        return this.root.sendTranslation(player, qualifyKey(key), args);
    }

    @Override
    public CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        return this.root.sendTranslationOrDefault(player, qualifyKey(key), defaultValue, args);
    }

    private String qualifyKey(String key) {
        String normalizedKey = requireKey(key);
        return this.path + "." + normalizedKey;
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
