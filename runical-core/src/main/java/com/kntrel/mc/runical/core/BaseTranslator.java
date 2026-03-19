package com.kntrel.mc.runical.core;

import java.util.concurrent.CompletableFuture;

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
     * @return the dot-separated path prefix, or an empty string for the root translator
     */
    String getPath();

    BaseRunical getRoot();

    /**
     * Returns a child translator rooted at the given path segment.
     *
     * <p>Segments are trimmed, must not be blank, and must not contain dots. Child translators
     * resolve keys relative to the returned path.
     *
     * @param segment direct child path segment
     * @return a child translator rooted under this translator
     * @throws NullPointerException if {@code segment} is {@code null}
     * @throws IllegalArgumentException if {@code segment} is blank or contains dots
     */
    BaseTranslator getChild(String segment);

    /**
     * Resolves a translation and returns either the rendered translation or the key when the
     * translation cannot be found.
     *
     * <p>Locale identifiers are normalized by trimming whitespace, converting underscores to
     * hyphens, and lower-casing the value. Placeholder values are rendered only when a translation
     * is found. Null placeholder entries are ignored, placeholder values are converted with
     * {@link String#valueOf(Object)}, missing placeholders remain unchanged, and literal braces can
     * be escaped with doubled braces such as <code>{{</code> and <code>}}</code>.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code key} when no translation was found
     * @throws NullPointerException if {@code locale} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code locale} or {@code key} is blank
     */
    default String translate(String locale, String key, Placeholder... args) {
        return this.resolve(locale, key, args).orKey();
    }

    /**
     * Resolves a translation without placeholders.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return the rendered translation, or {@code key} when no translation was found
     */
    default String translate(String locale, String key) {
        return this.translate(locale, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns either the rendered translation or the key
     * when the translation cannot be found.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code key}
     */
    default CompletableFuture<String> translateAsync(String locale, String key, Placeholder... args) {
        return this.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::orKey);
    }

    /**
     * Asynchronously resolves a translation without placeholders.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with the rendered translation or {@code key}
     */
    default CompletableFuture<String> translateAsync(String locale, String key) {
        return this.translateAsync(locale, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation and returns {@code null} when no translation is available.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code null} when the key could not be resolved
     */
    default String translateOrNull(String locale, String key, Placeholder... args) {
        return this.resolve(locale, key, args).value();
    }

    /**
     * Resolves a translation without placeholders and returns {@code null} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return the rendered translation, or {@code null} when the key could not be resolved
     */
    default String translateOrNull(String locale, String key) {
        return this.translateOrNull(locale, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation and returns {@code defaultValue} when no translation is available.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code defaultValue} when the key could not be resolved
     */
    String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args);

    /**
     * Resolves a translation without placeholders and returns {@code defaultValue} when
     * unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @return the rendered translation, or {@code defaultValue} when the key could not be resolved
     */
    default String translateOrDefault(String locale, String key, String defaultValue) {
        return this.translateOrDefault(locale, key, defaultValue, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns {@code null} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code null}
     */
    default CompletableFuture<String> translateOrNullAsync(String locale, String key, Placeholder... args) {
        return this.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::value);
    }

    /**
     * Asynchronously resolves a translation without placeholders and returns {@code null} when
     * unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with the rendered translation or {@code null}
     */
    default CompletableFuture<String> translateOrNullAsync(String locale, String key) {
        return this.translateOrNullAsync(locale, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns {@code defaultValue} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code defaultValue}
     */
    CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args);

    /**
     * Asynchronously resolves a translation without placeholders and returns {@code defaultValue}
     * when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param defaultValue fallback value returned when the key could not be resolved
     * @return a future completing with the rendered translation or {@code defaultValue}
     */
    default CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue) {
        return this.translateOrDefaultAsync(locale, key, defaultValue, new Placeholder[0]);
    }

    /**
     * Resolves a translation and returns full metadata about the lookup.
     *
     * <p>When a translation is found, the returned value contains the requested locale, the final
     * locale that supplied the translation, the rendered value, and the {@link ResolutionSource}
     * describing which fallback branch succeeded. When a translation is not found, the value and
     * resolved locale are {@code null} and the source is {@link ResolutionSource#UNRESOLVED}.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return detailed lookup metadata
     * @throws NullPointerException if {@code locale} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code locale} or {@code key} is blank
     */
    ResolvedTranslation resolve(String locale, String key, Placeholder... args);

    /**
     * Resolves a translation without placeholders and returns full lookup metadata.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return detailed lookup metadata
     */
    default ResolvedTranslation resolve(String locale, String key) {
        return this.resolve(locale, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns full lookup metadata.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @param args placeholders used to render the resolved translation
     * @return a future completing with detailed lookup metadata
     */
    CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args);

    /**
     * Asynchronously resolves a translation without placeholders and returns full lookup metadata.
     *
     * @param locale requested locale
     * @param key dot-separated translation key, resolved relative to {@link #getPath()} when this
     *            translator is a child node
     * @return a future completing with detailed lookup metadata
     */
    default CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key) {
        return this.resolveAsync(locale, key, new Placeholder[0]);
    }
}
