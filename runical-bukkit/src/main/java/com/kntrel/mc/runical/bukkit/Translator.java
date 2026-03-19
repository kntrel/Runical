package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslator;
import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit-specific translator API that adds player-aware overloads on top of the core translator
 * contract.
 *
 * <p>Like {@link BaseTranslator}, implementations may represent the root translator or a
 * path-scoped child translator. The player-aware methods derive the locale from the supplied
 * player and then apply the same translation semantics as the core API.
 */
public interface Translator extends BaseTranslator {

    /**
     * Returns a child translator rooted at the given path segment while preserving the Bukkit
     * player-aware API.
     *
     * @param segment direct child path segment
     * @return a child translator rooted under this translator
     * @throws NullPointerException if {@code segment} is {@code null}
     * @throws IllegalArgumentException if {@code segment} is blank or contains dots
     */
    @Override
    Translator getChild(String segment);

    /**
     * Resolves a translation for the player's locale and returns either the rendered translation or
     * the key when the translation cannot be found.
     *
     * <p>Placeholder rendering, unresolved behavior, and child-path qualification follow the same
     * rules as {@link BaseTranslator#translate(String, String, Placeholder...)}.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code key} when no translation was found
     * @throws NullPointerException if {@code player} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    default String translate(Player player, String key, Placeholder... args) {
        return this.resolve(player, key, args).orKey();
    }

    /**
     * Resolves a translation for the player's locale without placeholders.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return the rendered translation, or {@code key} when no translation was found
     */
    default String translate(Player player, String key) {
        return this.translate(player, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation for the player's locale and returns either the
     * rendered translation or the key when the translation cannot be found.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code key}
     */
    default CompletableFuture<String> translateAsync(Player player, String key, Placeholder... args) {
        return this.resolveAsync(player, key, args).thenApply(ResolvedTranslation::orKey);
    }

    /**
     * Asynchronously resolves a translation for the player's locale without placeholders.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with the rendered translation or {@code key}
     */
    default CompletableFuture<String> translateAsync(Player player, String key) {
        return this.translateAsync(player, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation for the player's locale and returns {@code null} when unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code null} when the key could not be resolved
     */
    default String translateOrNull(Player player, String key, Placeholder... args) {
        return this.resolve(player, key, args).value();
    }

    /**
     * Resolves a translation for the player's locale without placeholders and returns
     * {@code null} when unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return the rendered translation, or {@code null} when the key could not be resolved
     */
    default String translateOrNull(Player player, String key) {
        return this.translateOrNull(player, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation for the player's locale and returns {@code defaultValue} when no
     * translation is available.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @param args placeholders used to render the fallback value when the key is unresolved
     * @return the rendered translation, or {@code defaultValue} when the key could not be resolved
     * @throws NullPointerException if {@code player}, {@code key}, or {@code defaultValue} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    String translateOrDefault(Player player, String key, String defaultValue, Placeholder... args);

    /**
     * Resolves a translation for the player's locale without placeholders and returns
     * {@code defaultValue} when unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @return the rendered translation, or {@code defaultValue} when the key could not be resolved
     */
    default String translateOrDefault(Player player, String key, String defaultValue) {
        return this.translateOrDefault(player, key, defaultValue, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation for the player's locale and returns {@code null} when
     * unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code null}
     */
    default CompletableFuture<String> translateOrNullAsync(Player player, String key, Placeholder... args) {
        return this.resolveAsync(player, key, args).thenApply(ResolvedTranslation::value);
    }

    /**
     * Asynchronously resolves a translation for the player's locale without placeholders and
     * returns {@code null} when unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with the rendered translation or {@code null}
     */
    default CompletableFuture<String> translateOrNullAsync(Player player, String key) {
        return this.translateOrNullAsync(player, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation for the player's locale and returns
     * {@code defaultValue} when unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @param args placeholders used to render the fallback value when the key is unresolved
     * @return a future completing with the rendered translation or {@code defaultValue}
     */
    CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue, Placeholder... args);

    /**
     * Asynchronously resolves a translation for the player's locale without placeholders and
     * returns {@code defaultValue} when unavailable.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @return a future completing with the rendered translation or {@code defaultValue}
     */
    default CompletableFuture<String> translateOrDefaultAsync(Player player, String key, String defaultValue) {
        return this.translateOrDefaultAsync(player, key, defaultValue, new Placeholder[0]);
    }

    /**
     * Resolves a translation for the player's locale and returns full metadata about the lookup.
     *
     * <p>The returned {@link ResolvedTranslation} contains the locale derived from the player as
     * the requested locale, the locale that supplied the translation, the rendered value, and the
     * fallback branch that succeeded.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return detailed lookup metadata
     * @throws NullPointerException if {@code player} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    ResolvedTranslation resolve(Player player, String key, Placeholder... args);

    /**
     * Resolves a translation for the player's locale without placeholders and returns full lookup
     * metadata.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return detailed lookup metadata
     */
    default ResolvedTranslation resolve(Player player, String key) {
        return this.resolve(player, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation for the player's locale and returns full lookup
     * metadata.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with detailed lookup metadata
     */
    CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key, Placeholder... args);

    /**
     * Asynchronously resolves a translation for the player's locale without placeholders and
     * returns full lookup metadata.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with detailed lookup metadata
     */
    default CompletableFuture<ResolvedTranslation> resolveAsync(Player player, String key) {
        return this.resolveAsync(player, key, new Placeholder[0]);
    }

    /**
     * Formats a collection using the player's locale and {@link ListStyle#AND AND-style} list
     * rules.
     *
     * @param player player whose locale should be used
     * @param items items to format
     * @return the formatted list
     * @throws NullPointerException if {@code player} or {@code items} is {@code null}
     */
    default String formatList(Player player, Collection<?> items) {
        return this.formatList(player, items, ListStyle.AND);
    }

    /**
     * Formats a collection using locale-specific list metadata for the player's locale.
     *
     * @param player player whose locale should be used
     * @param items items to format
     * @param style list conjunction style
     * @return the formatted list
     * @throws NullPointerException if {@code player}, {@code items}, or {@code style} is
     *                              {@code null}
     */
    String formatList(Player player, Collection<?> items, ListStyle style);

    /**
     * Resolves a translation for the player's locale and schedules the rendered message to be sent
     * to that player.
     *
     * <p>The future completes with {@code true} when the message was delivered, or {@code false}
     * when the player was no longer online at send time.
     *
     * @param player player who should receive the message
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with whether the message was delivered
     * @throws NullPointerException if {@code player} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    CompletableFuture<Boolean> sendTranslation(Player player, String key, Placeholder... args);

    /**
     * Resolves a translation for the player's locale without placeholders and schedules the
     * rendered message to be sent to that player.
     *
     * @param player player who should receive the message
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with whether the message was delivered
     */
    default CompletableFuture<Boolean> sendTranslation(Player player, String key) {
        return this.sendTranslation(player, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation for the player's locale and schedules either the translation or the
     * rendered {@code defaultValue} to be sent to that player.
     *
     * <p>The future completes with {@code true} when the message was delivered, or {@code false}
     * when the player was no longer online at send time.
     *
     * @param player player who should receive the message
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value used when the key could not be resolved
     * @param args placeholders used to render the translation or fallback value
     * @return a future completing with whether the message was delivered
     * @throws NullPointerException if {@code player}, {@code key}, or {@code defaultValue} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue, Placeholder... args);

    /**
     * Resolves a translation for the player's locale without placeholders and schedules either the
     * translation or {@code defaultValue} to be sent to that player.
     *
     * @param player player who should receive the message
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value used when the key could not be resolved
     * @return a future completing with whether the message was delivered
     */
    default CompletableFuture<Boolean> sendTranslationOrDefault(Player player, String key, String defaultValue) {
        return this.sendTranslationOrDefault(player, key, defaultValue, new Placeholder[0]);
    }
}
