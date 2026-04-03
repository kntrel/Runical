package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.dsl.TranslationJob;

/**
 * Path-scoped translation API.
 *
 * <p>Implementations may represent the root translation engine or a child translator whose keys
 * are resolved relative to a fixed dot-separated path.
 */
public interface BaseTranslator {

    /**
     * Returns the translator path prefix.
     *
     * <p>The root translator returns an empty string.
     *
     * @return the dot-separated query path prefix, or an empty string for the root translator
     */
    String getPath();

    BaseRunical getRoot();

    /**
     * Returns a child translator rooted at the given path segment.
     *
     * <p>Segments are trimmed, must not be blank, and must not contain dots. Child translators
     * resolve keys relative to the returned translator scope.
     *
     * @param segment direct child path segment
     * @return a child translator rooted under this translator
     * @throws NullPointerException if {@code segment} is {@code null}
     * @throws IllegalArgumentException if {@code segment} is blank or contains dots
     */
    BaseTranslator getChild(String segment);

    /**
     * Starts a translation lookup job for the given locale and key.
     *
     * <p>The returned job is mutable and accumulates miss-handling intent until a terminal
     * operation such as {@link TranslationJob#message()} or
     * {@link TranslationJob#translation()} is invoked.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a mutable translation job
     * @throws NullPointerException if {@code locale} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code locale} or {@code key} is blank
     */
    TranslationJob translate(String locale, String key);
}
