package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.dsl.TranslationJob;

import java.util.Objects;

final class BaseTranslatorNode implements BaseTranslator {

    private final BaseRunical root_;
    private final String path_;

    BaseTranslatorNode(BaseRunical root, String path) {
        this.root_ = Objects.requireNonNull(root, "root");
        this.path_ = Objects.requireNonNull(path, "path");
    }

    @Override
    public String getPath() {
        return this.path_;
    }

    @Override
    public BaseRunical getRoot() {
        return this.root_;
    }

    @Override
    public BaseTranslator getChild(String segment) {
        return this.root_.childTranslator(qualifyPath(this.path_, segment));
    }

    @Override
    public TranslationJob translate(String locale, String key) {
        return this.root_.translate(locale, qualifyKey(key));
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
