package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.placeholder.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class TranslationJobImpl extends BaseBukkitTranslationJob {

    private final String locale_;

    TranslationJobImpl(Runical root, String locale, String key, Placeholder... args) {
        super(root, key, args);
        this.locale_ = Objects.requireNonNull(locale, "locale");
    }

    @Override
    protected ResolvedTranslation resolveRaw() {
        return this.root().resolveLocaleTranslation(this.locale_, this.key(), this.args());
    }

    @Override
    protected CompletableFuture<ResolvedTranslation> resolveRawAsync() {
        return this.root().resolveLocaleTranslationAsync(this.locale_, this.key(), this.args());
    }

    @Override
    protected String renderDefaultValue(String defaultValue) {
        return this.root().renderLocaleMessage(defaultValue, this.args());
    }
}
