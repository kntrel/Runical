package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslator;
import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class CompositeTranslator implements Translator {

    //FIELDS
    private final BaseTranslator delegate_;
    private final Runical root_;


    //CONSTRUCTOR
    private CompositeTranslator(BaseTranslator delegate) {
        this.delegate_ = Objects.requireNonNull(delegate, "delegate");
        if (!(delegate.getRoot() instanceof Runical runical)) {
            throw new IllegalArgumentException("Bukkit translator compositions must use Bukkit Runical roots.");
        }
        this.root_ = runical;
    }


    static Translator wrap(BaseTranslator translator) {
        if (translator instanceof Translator bukkitTranslator) {
            return bukkitTranslator;
        }
        return new CompositeTranslator(translator);
    }

    @Override
    public String getPath() {
        return this.delegate_.getPath();
    }

    @Override
    public Runical getRoot() {
        return this.root_;
    }

    @Override
    public Translator getChild(String segment) {
        return wrap(this.delegate_.getChild(segment));
    }

    @Override
    public String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args) {
        return this.delegate_.translateOrDefault(locale, key, defaultValue, args);
    }

    @Override
    public CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args) {
        return this.delegate_.translateOrDefaultAsync(locale, key, defaultValue, args);
    }

    @Override
    public ResolvedTranslation resolve(String locale, String key, Placeholder... args) {
        return this.delegate_.resolve(locale, key, args);
    }

    @Override
    public CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
        return this.delegate_.resolveAsync(locale, key, args);
    }

    @Override
    public String translateOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        return this.delegate_.translateOrDefault(this.root_.localeOf(player), key, defaultValue, args);
    }

    @Override
    public CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue, Placeholder... args) {
        return this.delegate_.translateOrDefaultAsync(this.root_.localeOf(player), key, defaultValue, args);
    }

    @Override
    public ResolvedTranslation resolve(Player player, String key, Placeholder... args) {
        return this.delegate_.resolve(this.root_.localeOf(player), key, args);
    }

    @Override
    public CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key, Placeholder... args) {
        return this.delegate_.resolveAsync(this.root_.localeOf(player), key, args);
    }

    @Override
    public String formatList(Player player, Collection<?> items, ListStyle style) {
        return this.root_.formatList(this.root_.localeOf(player), items, style);
    }

    @Override
    public CompletableFuture<Boolean> sendTranslation(Player player, String key, Placeholder... args) {
        String locale = this.root_.localeOf(player);
        return this.root_.sendMessage(player, this.delegate_.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::orKey));
    }

    @Override
    public CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue, Placeholder... args) {
        String locale = this.root_.localeOf(player);
        return this.root_.sendMessage(player, this.delegate_.translateOrDefaultAsync(locale, key, defaultValue, args));
    }
}
