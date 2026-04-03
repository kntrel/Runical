package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.argument.Argument;
import com.kntrel.mc.runical.core.ResolvedTranslation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class TranslationJobImpl extends BaseBukkitTranslationJob {

    private final String locale_;

    TranslationJobImpl(Runical root, String locale, String key) {
        super(root, key);
        this.locale_ = Objects.requireNonNull(locale, "locale");
    }

    @Override
    protected ResolvedTranslation resolveRaw(Argument[] args) {
        return this.root().resolveLocaleTranslation(this.locale_, this.key(), args);
    }

    @Override
    protected CompletableFuture<ResolvedTranslation> resolveRawAsync(Argument[] args) {
        return this.root().resolveLocaleTranslationAsync(this.locale_, this.key(), args);
    }

    @Override
    protected String renderDefaultValue(String defaultValue, Argument[] args) {
        return this.root().renderLocaleMessage(defaultValue, args);
    }
}
