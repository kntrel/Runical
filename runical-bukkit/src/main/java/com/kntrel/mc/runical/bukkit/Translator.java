package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslator;
import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.placeholder.Placeholder;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.TranslationJob;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Bukkit-specific translator API that adds player-aware lookup entrypoints and list formatting on
 * top of the core translator contract.
 */
public interface Translator extends BaseTranslator {

    /**
     * Returns a child translator rooted at the given path segment while preserving the Bukkit API.
     *
     * @param segment direct child path segment
     * @return a child translator rooted under this translator
     * @throws NullPointerException if {@code segment} is {@code null}
     * @throws IllegalArgumentException if {@code segment} is blank or contains dots
     */
    @Override
    Translator getChild(String segment);

    /**
     * Starts a translation lookup job for the given locale and key.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a mutable translation job
     */
    @Override
    TranslationJob translate(String locale, String key, Placeholder... args);

    /**
     * Starts a translation lookup job without placeholders.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a mutable translation job
     */
    @Override
    default TranslationJob translate(String locale, String key) {
        return this.translate(locale, key, new Placeholder[0]);
    }

    /**
     * Starts a translation lookup job using the supplied player's locale.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a player-bound translation job
     */
    PlayerTranslationJob translate(Player player, String key, Placeholder... args);

    /**
     * Starts a translation lookup job using the supplied player's locale without placeholders.
     *
     * @param player player whose locale should be used
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a player-bound translation job
     */
    default PlayerTranslationJob translate(Player player, String key) {
        return this.translate(player, key, new Placeholder[0]);
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
}
