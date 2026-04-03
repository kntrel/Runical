package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.argument.Argument;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class TranslationJobImpl extends BaseTranslationJob {

    private final BaseRunical root_;
    private final String locale_;
    private final String key_;

    TranslationJobImpl(BaseRunical root, String locale, String key) {
        super();
        this.root_ = Objects.requireNonNull(root, "root");
        this.locale_ = Objects.requireNonNull(locale, "locale");
        this.key_ = Objects.requireNonNull(key, "key");
    }

    @Override
    protected ResolvedTranslation resolveRaw(Argument[] args) {
        return this.root_.resolveTranslation(this.locale_, this.key_, args);
    }

    @Override
    protected CompletableFuture<ResolvedTranslation> resolveRawAsync(Argument[] args) {
        return this.root_.resolveTranslationAsync(this.locale_, this.key_, args);
    }

    @Override
    protected String renderDefaultValue(String defaultValue, Argument[] args) {
        return this.root_.renderMessageTemplate(defaultValue, args);
    }
}
