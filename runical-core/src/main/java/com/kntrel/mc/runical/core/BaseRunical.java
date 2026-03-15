package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.internal.ListFormat;
import com.kntrel.mc.runical.core.internal.LocaleBundle;
import com.kntrel.mc.runical.core.internal.LocaleIndex;
import com.kntrel.mc.runical.core.internal.LocaleSupport;
import com.kntrel.mc.runical.core.internal.PlaceholderRenderer;
import com.kntrel.mc.runical.core.internal.YamlLocaleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base implementation of the Runical locale resolver.
 *
 * <p>Instances scan a directory of YAML locale files whose file names map to normalized locale
 * identifiers such as {@code en-us.yml} or {@code es.yml}. Nested YAML objects are flattened into
 * dot-separated translation keys, while the reserved {@code _runical} root is used for metadata
 * such as localized list formatting patterns.
 *
 * <p>Translation lookup follows this fallback order:
 * exact locale, general language locale, sibling regional locales for the same language,
 * configured default locale, and finally the unresolved key.
 *
 * <p>This type is safe to use concurrently. Async methods run on the configured executor from
 * {@link RunicalOptions}, or on a virtual-thread-per-task executor when none is provided.
 *
 * <p>Subclass this type to expose a concrete constructor or to integrate locale retention with a
 * higher-level platform abstraction.
 *
 * <p>All public instance methods except {@link #close()} throw {@link IllegalStateException} after
 * the instance has been closed.
 */
public abstract class BaseRunical implements AutoCloseable {

    //CONSTANTS
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseRunical.class);


    //FIELDS
    private final Path languagesDirectory;
    private final RunicalOptions options;
    private final Executor asyncExecutor;
    private final boolean shutdownAsyncExecutorOnClose;
    private final AtomicReference<LocaleIndex> localeIndex;
    private final AtomicReference<String> defaultLocale;
    private final AtomicLong querySequence;
    private final AtomicLong indexVersion;
    private final AtomicBoolean closed;
    private final ReentrantLock evictionLock;
    private final ConcurrentHashMap<String, LocaleBundle> loadedLocales;
    private final ConcurrentHashMap<String, CompletableFuture<LocaleBundle>> inFlightLoads;
    private final ConcurrentHashMap<String, AtomicInteger> retainedLocales;


    //CONSTRUCTOR
    /**
     * Creates a resolver backed by the given language directory.
     *
     * <p>The directory path is normalized to an absolute path and scanned immediately by invoking
     * {@link #reload()}. When {@code options} is {@code null}, the default {@link RunicalOptions}
     * are used.
     *
     * @param languagesDirectory directory containing locale YAML files
     * @param options immutable resolver options, or {@code null} to use defaults
     * @throws NullPointerException if {@code languagesDirectory} is {@code null}
     */
    protected BaseRunical(Path languagesDirectory, RunicalOptions options) {
        this.languagesDirectory = Objects.requireNonNull(languagesDirectory, "languagesDirectory").toAbsolutePath().normalize();
        this.options = options == null ? RunicalOptions.builder().build() : options;
        this.localeIndex = new AtomicReference<>(LocaleIndex.empty());
        this.defaultLocale = new AtomicReference<>(this.options.defaultLocale());
        this.querySequence = new AtomicLong();
        this.indexVersion = new AtomicLong();
        this.closed = new AtomicBoolean();
        this.evictionLock = new ReentrantLock();
        this.loadedLocales = new ConcurrentHashMap<>();
        this.inFlightLoads = new ConcurrentHashMap<>();
        this.retainedLocales = new ConcurrentHashMap<>();

        Executor configuredExecutor = this.options.asyncExecutor();
        if (configuredExecutor != null) {
            this.asyncExecutor = configuredExecutor;
            this.shutdownAsyncExecutorOnClose = this.options.shutdownAsyncExecutorOnClose();
        } else {
            this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
            this.shutdownAsyncExecutorOnClose = true;
        }

        this.reload();
    }


    //API
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
     * @param key dot-separated translation key
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code key} when no translation was found
     * @throws NullPointerException if {@code locale} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code locale} or {@code key} is blank
     */
    public final String translate(String locale, String key, Placeholder... args) {
        return this.resolve(locale, key, args).orKey();
    }

    /**
     * Resolves a translation without placeholders.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @return the rendered translation, or {@code key} when no translation was found
     */
    public final String translate(String locale, String key) {
        return translate(locale, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns either the rendered translation or the key
     * when the translation cannot be found.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code key}
     */
    public final CompletableFuture<String> translateAsync(String locale, String key, Placeholder... args) {
        return this.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::orKey);
    }

    /**
     * Asynchronously resolves a translation without placeholders.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @return a future completing with the rendered translation or {@code key}
     */
    public final CompletableFuture<String> translateAsync(String locale, String key) {
        return this.translateAsync(locale, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation and returns {@code null} when no translation is available.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code null} when the key could not be resolved
     */
    public final String translateOrNull(String locale, String key, Placeholder... args) {
        return this.resolve(locale, key, args).value();
    }

    /**
     * Resolves a translation without placeholders and returns {@code null} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @return the rendered translation, or {@code null} when the key could not be resolved
     */
    public final String translateOrNull(String locale, String key) {
        return this.translateOrNull(locale, key, new Placeholder[0]);
    }

    /**
     * Resolves a translation and returns {@code defaultValue} when no translation is available.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param defaultValue fallback value returned when the key could not be resolved
     * @param args placeholders used to render the resolved translation
     * @return the rendered translation, or {@code defaultValue} when the key could not be resolved
     */
    public final String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        String value = this.resolve(locale, key, args).value();
        return value != null ? value : this.renderMessage(defaultValue, args);
    }

    /**
     * Resolves a translation without placeholders and returns {@code defaultValue} when
     * unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param defaultValue fallback value returned when the key could not be resolved
     * @return the rendered translation, or {@code defaultValue} when the key could not be resolved
     */
    public final String translateOrDefault(String locale, String key, String defaultValue) {
        return this.translateOrDefault(locale, key, defaultValue, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns {@code null} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code null}
     */
    public final CompletableFuture<String> translateOrNullAsync(String locale, String key, Placeholder... args) {
        return this.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::value);
    }

    /**
     * Asynchronously resolves a translation without placeholders and returns {@code null} when
     * unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @return a future completing with the rendered translation or {@code null}
     */
    public final CompletableFuture<String> translateOrNullAsync(String locale, String key) {
        return this.translateOrNullAsync(locale, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns {@code defaultValue} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param defaultValue fallback value returned when the key could not be resolved
     * @param args placeholders used to render the resolved translation
     * @return a future completing with the rendered translation or {@code defaultValue}
     */
    public final CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return this.resolveAsync(locale, key, args).thenApply(resolved -> {
            String value = resolved.value();
            return value != null ? value : renderMessage(defaultValue, args);
        });
    }

    /**
     * Asynchronously resolves a translation without placeholders and returns
     * {@code defaultValue} when unavailable.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param defaultValue fallback value returned when the key could not be resolved
     * @return a future completing with the rendered translation or {@code defaultValue}
     */
    public final CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue) {
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
     * @param key dot-separated translation key
     * @param args placeholders used to render the resolved translation
     * @return detailed lookup metadata
     * @throws NullPointerException if {@code locale} or {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code locale} or {@code key} is blank
     */
    public final ResolvedTranslation resolve(String locale, String key, Placeholder... args) {
        this.ensureOpen();
        String normalizedLocale = this.normalizeLocale(locale);
        String normalizedKey = requireKey(key);
        long accessSequence = this.querySequence.incrementAndGet();

        ResolvedTranslation resolved = this.resolveValue(normalizedLocale, normalizedKey, accessSequence);
        if (!resolved.found()) {
            cleanupIfNeeded(accessSequence);
            return resolved;
        }

        String rendered = renderMessage(resolved.value(), args);
        cleanupIfNeeded(accessSequence);
        return new ResolvedTranslation(
                resolved.requestedLocale(),
                resolved.key(),
                resolved.resolvedLocale(),
                rendered,
                resolved.source()
        );
    }

    /**
     * Resolves a translation without placeholders and returns full lookup metadata.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @return detailed lookup metadata
     */
    public final ResolvedTranslation resolve(String locale, String key) {
        return resolve(locale, key, new Placeholder[0]);
    }

    /**
     * Asynchronously resolves a translation and returns full lookup metadata.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @param args placeholders used to render the resolved translation
     * @return a future completing with detailed lookup metadata
     */
    public final CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
        ensureOpen();
        return CompletableFuture.supplyAsync(() -> resolve(locale, key, args), this.asyncExecutor);
    }

    /**
     * Asynchronously resolves a translation without placeholders and returns full lookup metadata.
     *
     * @param locale requested locale
     * @param key dot-separated translation key
     * @return a future completing with detailed lookup metadata
     */
    public final CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key) {
        return resolveAsync(locale, key, new Placeholder[0]);
    }

    /**
     * Formats a collection using the locale's {@link ListStyle#AND AND-style} list patterns.
     *
     * @param locale requested locale
     * @param items items to format
     * @return the formatted list
     * @throws NullPointerException if {@code locale} or {@code items} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final String formatList(String locale, Collection<?> items) {
        return formatList(locale, items, ListStyle.AND);
    }

    /**
     * Formats a collection using locale-specific list metadata from the {@code _runical} section
     * of the locale file.
     *
     * <p>List-format lookup follows the same locale fallback order as translations. When no custom
     * metadata is available, built-in English defaults are used for the requested {@code style}.
     * Item values are converted with {@link String#valueOf(Object)}.
     *
     * @param locale requested locale
     * @param items items to format
     * @param style list conjunction style
     * @return the formatted list
     * @throws NullPointerException if {@code locale}, {@code items}, or {@code style} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final String formatList(String locale, Collection<?> items, ListStyle style) {
        ensureOpen();
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(style, "style");

        String normalizedLocale = this.normalizeLocale(locale);
        long accessSequence = this.querySequence.incrementAndGet();
        ListFormat listFormat = resolveListFormat(normalizedLocale, style, accessSequence);
        String formatted = listFormat.format(items);
        cleanupIfNeeded(accessSequence);
        return formatted;
    }

    /**
     * Returns the normalized default locale used as the final fallback for translations and list
     * formats.
     *
     * @return the current default locale
     */
    public final String getDefaultLocale() {
        return this.defaultLocale.get();
    }

    /**
     * Updates the default locale used as the final fallback for lookups.
     *
     * <p>The new locale is normalized immediately.
     *
     * @param locale new default locale
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final void setDefaultLocale(String locale) {
        this.defaultLocale.set(this.normalizeLocale(locale));
    }

    /**
     * Returns whether the current locale index contains a file for the given locale.
     *
     * <p>This checks the scanned file index only and does not load the locale into memory.
     *
     * @param locale locale to check
     * @return {@code true} when the locale has a corresponding YAML file
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final boolean hasLocale(String locale) {
        ensureOpen();
        return this.localeIndex.get().hasLocale(locale);
    }

    /**
     * Returns whether the given locale is currently loaded in the in-memory cache.
     *
     * @param locale locale to inspect
     * @return {@code true} when the locale bundle is currently cached
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final boolean isLoaded(String locale) {
        ensureOpen();
        return this.loadedLocales.containsKey(this.normalizeLocale(locale));
    }

    /**
     * Loads a locale into memory ahead of time if it exists.
     *
     * <p>If the locale file is missing or fails to load, this method returns silently and the
     * locale remains unavailable.
     *
     * @param locale locale to preload
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final void preload(String locale) {
        ensureOpen();
        String normalizedLocale = this.normalizeLocale(locale);
        long accessSequence = this.querySequence.incrementAndGet();
        touchOrLoadBundle(normalizedLocale, accessSequence);
        cleanupIfNeeded(accessSequence);
    }

    /**
     * Rescans the language directory and clears all cached locale bundles.
     *
     * <p>Existing in-flight loads are discarded from the cache view by incrementing the index
     * version. The current default locale is not changed.
     */
    public final void reload() {
        ensureOpenOrFresh();
        this.indexVersion.incrementAndGet();
        this.localeIndex.set(LocaleIndex.scan(this.languagesDirectory, LOGGER));
        this.loadedLocales.clear();
        this.inFlightLoads.clear();
    }

    /**
     * Removes a single locale bundle from the in-memory cache.
     *
     * @param locale locale to evict
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    public final void evict(String locale) {
        ensureOpen();
        this.loadedLocales.remove(this.normalizeLocale(locale));
    }

    /**
     * Removes all locale bundles from the in-memory cache.
     */
    public final void evictAll() {
        ensureOpen();
        this.loadedLocales.clear();
    }

    /**
     * Clears cached locale state and optionally shuts down the async executor.
     *
     * <p>The executor is shut down only when it is an {@link ExecutorService} and
     * {@link RunicalOptions#shutdownAsyncExecutorOnClose()} is {@code true}. This method is
     * idempotent.
     */
    @Override public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        this.loadedLocales.clear();
        this.inFlightLoads.clear();
        this.retainedLocales.clear();

        if (this.shutdownAsyncExecutorOnClose && this.asyncExecutor instanceof ExecutorService executorService) {
            executorService.shutdown();
        }
    }



    //HELPERS
    /**
     * Normalizes a locale identifier using Runical's locale rules.
     *
     * <p>Normalization trims surrounding whitespace, replaces underscores with hyphens, and
     * lower-cases the value.
     *
     * @param locale locale to normalize
     * @return the normalized locale
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    protected final String normalizeLocale(String locale) {
        return LocaleSupport.normalizeLocale(locale);
    }

    /**
     * Increments the retention count for a locale so cache cleanup will not evict it.
     *
     * <p>Subclasses should pair each call with {@link #releaseLocale(String)} once the locale is no
     * longer actively referenced. Retaining a locale does not force it to load immediately.
     *
     * @param locale locale to retain
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    protected final void retainLocale(String locale) {
        String normalizedLocale = this.normalizeLocale(locale);
        this.retainedLocales.compute(normalizedLocale, (ignored, count) -> {
            if (count == null) {
                return new AtomicInteger(1);
            }
            count.incrementAndGet();
            return count;
        });
    }

    /**
     * Decrements the retention count for a locale and evicts it immediately when the count reaches
     * zero.
     *
     * @param locale locale to release
     * @throws NullPointerException if {@code locale} is {@code null}
     * @throws IllegalArgumentException if {@code locale} is blank
     */
    protected final void releaseLocale(String locale) {
        String normalizedLocale = this.normalizeLocale(locale);
        AtomicBoolean evict = new AtomicBoolean();
        this.retainedLocales.computeIfPresent(normalizedLocale, (ignored, count) -> {
            if (count.decrementAndGet() <= 0) {
                evict.set(true);
                return null;
            }
            return count;
        });
        if (evict.get()) {
            this.loadedLocales.remove(normalizedLocale);
        }
    }

    /**
     * Moves an existing retention from one locale to another.
     *
     * <p>If both locales normalize to the same value, no action is taken.
     *
     * @param oldLocale previously retained locale
     * @param newLocale new locale to retain
     * @throws NullPointerException if either locale is {@code null}
     * @throws IllegalArgumentException if either locale is blank
     */
    protected final void replaceRetainedLocale(String oldLocale, String newLocale) {
        String previous = this.normalizeLocale(oldLocale);
        String current = this.normalizeLocale(newLocale);
        if (previous.equals(current)) {
            return;
        }
        this.releaseLocale(previous);
        this.retainLocale(current);
    }
    private ResolvedTranslation resolveValue(String locale, String key, long accessSequence) {
        Optional<String> exactValue = this.findMessage(locale, key, accessSequence);
        if (exactValue.isPresent()) {
            return new ResolvedTranslation(locale, key, locale, exactValue.get(), ResolutionSource.EXACT);
        }

        String generalLocale = LocaleSupport.generalLocale(locale);
        if (!generalLocale.equals(locale)) {
            Optional<String> generalValue = this.findMessage(generalLocale, key, accessSequence);
            if (generalValue.isPresent()) {
                return new ResolvedTranslation(locale, key, generalLocale, generalValue.get(), ResolutionSource.GENERAL);
            }
        }

        List<String> siblingLocales = this.siblingLocales(locale, generalLocale);
        if (!siblingLocales.isEmpty()) {
            LOGGER.warn(
                    "Unable to resolve key '{}' for locale '{}'; trying sibling locale fallbacks for language '{}'.",
                    key,
                    locale,
                    LocaleSupport.languageOf(locale)
            );
            for (String siblingLocale : siblingLocales) {
                Optional<String> siblingValue = this.findMessage(siblingLocale, key, accessSequence);
                if (siblingValue.isPresent()) {
                    return new ResolvedTranslation(locale, key, siblingLocale, siblingValue.get(), ResolutionSource.SIBLING);
                }
            }
        }

        String defaultLocale = this.defaultLocale.get();
        if (!this.alreadyAttempted(locale, generalLocale, defaultLocale, siblingLocales)) {
            LOGGER.warn(
                    "Unable to resolve key '{}' for locale '{}'; falling back to default locale '{}'.",
                    key,
                    locale,
                    defaultLocale
            );

            Optional<String> defaultValue = this.findMessage(defaultLocale, key, accessSequence);
            if (defaultValue.isPresent()) {
                return new ResolvedTranslation(locale, key, defaultLocale, defaultValue.get(), ResolutionSource.DEFAULT);
            }
        }

        LOGGER.error(
                "Unable to resolve key '{}' for locale '{}' or default locale '{}'; returning the key itself.",
                key,
                locale,
                defaultLocale
        );
        return new ResolvedTranslation(locale, key, null, null, ResolutionSource.UNRESOLVED);
    }
    private Optional<String> findMessage(String locale, String key, long accessSequence) {
        LocaleBundle bundle = this.touchOrLoadBundle(locale, accessSequence);
        if (bundle == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bundle.message(key));
    }
    private ListFormat resolveListFormat(String locale, ListStyle style, long accessSequence) {
        Optional<ListFormat> exact = findListFormat(locale, style, accessSequence);
        if (exact.isPresent()) {
            return exact.get();
        }

        String generalLocale = LocaleSupport.generalLocale(locale);
        if (!generalLocale.equals(locale)) {
            Optional<ListFormat> general = findListFormat(generalLocale, style, accessSequence);
            if (general.isPresent()) {
                return general.get();
            }
        }

        for (String siblingLocale : siblingLocales(locale, generalLocale)) {
            Optional<ListFormat> sibling = findListFormat(siblingLocale, style, accessSequence);
            if (sibling.isPresent()) {
                return sibling.get();
            }
        }

        String defaultLocale = this.defaultLocale.get();
        if (!alreadyAttempted(locale, generalLocale, defaultLocale, List.of())) {
            Optional<ListFormat> defaultValue = this.findListFormat(defaultLocale, style, accessSequence);
            if (defaultValue.isPresent()) {
                return defaultValue.get();
            }
        }

        return ListFormat.defaultFor(style);
    }
    private Optional<ListFormat> findListFormat(String locale, ListStyle style, long accessSequence) {
        LocaleBundle bundle = touchOrLoadBundle(locale, accessSequence);
        if (bundle == null) {
            return Optional.empty();
        }
        return bundle.listFormat(style);
    }
    private List<String> siblingLocales(String locale, String generalLocale) {
        String language = LocaleSupport.languageOf(locale);
        List<String> siblings = new ArrayList<>();
        for (String candidate : this.localeIndex.get().localesForLanguage(language)) {
            if (candidate.equals(locale) || candidate.equals(generalLocale)) {
                continue;
            }
            if (LocaleSupport.isRegional(candidate)) {
                siblings.add(candidate);
            }
        }
        return siblings;
    }
    private boolean alreadyAttempted(String requestedLocale, String generalLocale, String defaultLocale, List<String> siblingLocales) {
        if (requestedLocale.equals(defaultLocale) || generalLocale.equals(defaultLocale)) {
            return true;
        }
        return siblingLocales.contains(defaultLocale);
    }
    private LocaleBundle touchOrLoadBundle(String locale, long accessSequence) {
        LocaleBundle loadedBundle = this.loadedLocales.get(locale);
        if (loadedBundle != null) {
            loadedBundle.touch(accessSequence);
            return loadedBundle;
        }

        Optional<Path> bundlePath = this.localeIndex.get().pathFor(locale);
        if (bundlePath.isEmpty()) {
            return null;
        }

        long loadVersion = this.indexVersion.get();
        CompletableFuture<LocaleBundle> createdFuture = new CompletableFuture<>();
        CompletableFuture<LocaleBundle> inFlight = this.inFlightLoads.putIfAbsent(locale, createdFuture);
        if (inFlight != null) {
            return inFlight.join();
        }

        try {
            LocaleBundle bundle = YamlLocaleLoader.load(locale, bundlePath.get(), loadVersion, LOGGER);
            bundle.touch(accessSequence);
            if (loadVersion == this.indexVersion.get()) {
                this.loadedLocales.put(locale, bundle);
                this.enforceLoadedLocaleLimit();
            }
            createdFuture.complete(bundle);
            return bundle;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load locale '{}' from '{}'.", locale, bundlePath.get(), exception);
            createdFuture.complete(null);
            return null;
        } finally {
            this.inFlightLoads.remove(locale, createdFuture);
        }
    }
    private String renderMessage(String template, Placeholder... args) {
        Map<String, String> placeholders = new HashMap<>();
        for (Placeholder argument : args) {
            if (argument == null) {
                continue;
            }
            placeholders.put(argument.name(), String.valueOf(argument.value()));
        }
        return PlaceholderRenderer.render(template, placeholders::get);
    }
    private void cleanupIfNeeded(long accessSequence) {
        if (accessSequence % this.options.cleanupIntervalQueries() != 0L) {
            return;
        }
        this.enforceLoadedLocaleLimit();
        this.evictIdleLocales(accessSequence);
    }
    private void enforceLoadedLocaleLimit() {
        int maxLoadedLocales = this.options.maxLoadedLocales();
        if (maxLoadedLocales <= 0 || this.loadedLocales.size() <= maxLoadedLocales) {
            return;
        }

        this.evictionLock.lock();
        try {
            while (this.loadedLocales.size() > maxLoadedLocales) {
                String candidate = this.loadedLocales.entrySet().stream()
                        .filter(entry -> !isRetained(entry.getKey()))
                        .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessSequence()))
                        .map(Map.Entry::getKey)
                        .orElse(null);

                if (candidate == null) {
                    return;
                }
                this.loadedLocales.remove(candidate);
            }
        } finally {
            this.evictionLock.unlock();
        }
    }
    private void evictIdleLocales(long accessSequence) {
        long idleThreshold = this.options.idleQueryThreshold();
        if (idleThreshold <= 0L) {
            return;
        }

        this.evictionLock.lock();
        try {
            for (Map.Entry<String, LocaleBundle> entry : this.loadedLocales.entrySet()) {
                if (isRetained(entry.getKey())) {
                    continue;
                }
                if (accessSequence - entry.getValue().lastAccessSequence() >= idleThreshold) {
                    this.loadedLocales.remove(entry.getKey(), entry.getValue());
                }
            }
        } finally {
            this.evictionLock.unlock();
        }
    }
    private boolean isRetained(String locale) {
        AtomicInteger retainedCount = this.retainedLocales.get(locale);
        return retainedCount != null && retainedCount.get() > 0;
    }
    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Translation key must not be blank.");
        }
        return key;
    }
    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Runical is closed.");
        }
    }
    private void ensureOpenOrFresh() {
        if (this.closed.get()) {
            throw new IllegalStateException("Runical is closed.");
        }
    }
}
