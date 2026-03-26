package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.internal.ListFormat;
import com.kntrel.mc.runical.core.internal.LocaleBundle;
import com.kntrel.mc.runical.core.internal.LocaleIndex;
import com.kntrel.mc.runical.core.internal.LocaleSupport;
import com.kntrel.mc.runical.core.internal.PlaceholderFlattener;
import com.kntrel.mc.runical.core.internal.PlaceholderRenderer;
import com.kntrel.mc.runical.core.internal.YamlLocaleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
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
import java.util.stream.Stream;

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
 * higher-level platform abstraction. {@link BaseRunical} also implements {@link BaseTranslator}
 * and acts as the root of a translator tree, where child translators qualify keys relative to a
 * fixed dot-separated path.
 *
 * <p>All public instance methods except {@link #close()} and {@link #getPath()} throw
 * {@link IllegalStateException} after the instance has been closed.
 */
public abstract class BaseRunical implements BaseTranslator, AutoCloseable {

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
    private final ConcurrentHashMap<String, BaseTranslator> childTranslators;
    private final AtomicReference<MountedAliases> mountedAliases;


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
        this.childTranslators = new ConcurrentHashMap<>();
        this.mountedAliases = new AtomicReference<>(MountedAliases.empty());

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
    @Override
    public final String getPath() {
        return "";
    }

    @Override
    public final BaseRunical getRoot() {
        return this;
    }

    @Override
    public BaseTranslator getChild(String segment) {
        return this.childTranslator(requireChildPath(segment));
    }

    /**
     * Registers a root alias so queries under {@code aliasPath} resolve through
     * {@code canonicalPath}.
     *
     * <p>Alias paths become real query paths on this root. Child translators created from an alias
     * path therefore round-trip honestly through {@link #getRoot()} and {@link BaseTranslator#getPath()}.
     *
     * @param canonicalPath existing query path that should supply the mounted subtree
     * @param aliasPath new query path that should point at the canonical subtree
     * @return this root for chaining
     * @throws NullPointerException if either path is {@code null}
     * @throws IllegalArgumentException if either path is blank, if {@code aliasPath} overlaps an
     *                                  existing alias, or if the alias would resolve to itself
     * @throws IllegalStateException if this root has been closed
     */
    public BaseRunical mount(String canonicalPath, String aliasPath) {
        this.ensureOpen();
        String normalizedCanonicalPath = normalizeTranslatorPath(canonicalPath, "canonicalPath");
        String normalizedAliasPath = normalizeTranslatorPath(aliasPath, "aliasPath");

        this.mountedAliases.updateAndGet(aliases -> aliases.withMount(normalizedCanonicalPath, normalizedAliasPath));
        return this;
    }

    /**
     * Registers a root alias that exposes the mounted translator at the given alias path.
     *
     * @param toMount canonical translator whose subtree should be mounted
     * @param aliasPath new query path that should point at the mounted subtree
     * @return this root for chaining
     * @throws NullPointerException if {@code toMount} or {@code aliasPath} is {@code null}
     * @throws IllegalArgumentException if the translator belongs to a different root, does not
     *                                  expose a non-root path, or the alias path is invalid
     * @throws IllegalStateException if this root has been closed
     */
    public BaseRunical mount(BaseTranslator toMount, String aliasPath) {
        BaseTranslator mountedTranslator = Objects.requireNonNull(toMount, "toMount");
        if (mountedTranslator.getRoot() != this) {
            throw new IllegalArgumentException("Mounted translators must share the same root.");
        }
        return this.mount(requireMountedTranslatorPath(mountedTranslator, "toMount"), aliasPath);
    }

    /**
     * Registers a root alias by mounting {@code toMount} beneath {@code child} at
     * {@code relativePath}.
     *
     * <p>For example, mounting {@code hierarchy} beneath {@code totem.deeds} at {@code hierarchy}
     * creates the alias {@code totem.deeds.hierarchy -> hierarchy}.
     *
     * @param child base translator whose visible path should receive the alias
     * @param toMount translator whose subtree should be mounted
     * @param relativePath relative path beneath {@code child} where the alias should appear
     * @return this root for chaining
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if either translator belongs to a different root, if
     *                                  {@code child} does not expose a path, if {@code toMount}
     *                                  does not expose a non-root path, or if {@code relativePath}
     *                                  is invalid
     * @throws IllegalStateException if this root has been closed
     */
    public BaseRunical mount(BaseTranslator child, BaseTranslator toMount, String relativePath) {
        BaseTranslator baseChild = Objects.requireNonNull(child, "child");
        BaseTranslator mountedTranslator = Objects.requireNonNull(toMount, "toMount");
        if (baseChild.getRoot() != this) {
            throw new IllegalArgumentException("Child translator must share the same root.");
        }
        if (mountedTranslator.getRoot() != this) {
            throw new IllegalArgumentException("Mounted translator must share the same root.");
        }

        String basePath = requireVisibleTranslatorPath(baseChild, "child");
        String normalizedRelativePath = normalizeTranslatorPath(relativePath, "relativePath");
        String aliasPath = basePath.isEmpty() ? normalizedRelativePath : basePath + "." + normalizedRelativePath;

        return this.mount(requireMountedTranslatorPath(mountedTranslator, "toMount"), aliasPath);
    }

    /** {@inheritDoc} */
    @Override
    public final String translateOrDefault(String locale, String key, String defaultValue, Placeholder... args) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        String value = this.resolve(locale, key, args).value();
        return value != null ? value : this.renderMessage(defaultValue, args);
    }

    /** {@inheritDoc} */
    @Override
    public final CompletableFuture<String> translateOrDefaultAsync(String locale, String key, String defaultValue, Placeholder... args) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return this.resolveAsync(locale, key, args).thenApply(resolved -> {
            String value = resolved.value();
            return value != null ? value : renderMessage(defaultValue, args);
        });
    }

    /** {@inheritDoc} */
    @Override
    public final ResolvedTranslation resolve(String locale, String key, Placeholder... args) {
        this.ensureOpen();
        String normalizedLocale = this.normalizeLocale(locale);
        String normalizedKey = requireKey(key);
        String lookupKey = this.mountedAliases.get().rewrite(normalizedKey);
        long accessSequence = this.querySequence.incrementAndGet();

        ResolvedTranslation resolved = this.resolveValue(normalizedLocale, lookupKey, accessSequence);
        if (!resolved.found()) {
            cleanupIfNeeded(accessSequence);
            return adaptResolvedKey(resolved, normalizedKey);
        }

        String rendered = renderMessage(resolved.value(), args);
        cleanupIfNeeded(accessSequence);
        return new ResolvedTranslation(
                resolved.requestedLocale(),
                normalizedKey,
                resolved.resolvedLocale(),
                rendered,
                resolved.source()
        );
    }

    /** {@inheritDoc} */
    @Override
    public final CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
        ensureOpen();
        return CompletableFuture.supplyAsync(() -> resolve(locale, key, args), this.asyncExecutor);
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

        this.childTranslators.clear();
        this.loadedLocales.clear();
        this.inFlightLoads.clear();
        this.retainedLocales.clear();
        this.mountedAliases.set(MountedAliases.empty());

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

    /**
     * Resolves a locale file when it is missing from the scanned language directory.
     *
     * <p>Subclasses can override this hook to lazily materialize locale files from another source,
     * such as a bundled resource inside a plugin jar. The returned path should point to the file on
     * disk that can be loaded by Runical.
     *
     * @param locale normalized locale identifier being requested
     * @return the on-disk locale file path, or an empty result when no fallback file exists
     */
    protected Optional<Path> resolveMissingLocaleFile(String locale) {
        return Optional.empty();
    }

    /**
     * Resolves a filesystem path referenced by a {@code !file} tag when it is missing on disk.
     *
     * <p>Subclasses can override this hook to lazily materialize tagged translation files from
     * another source, such as bundled plugin resources. Both paths are absolute and normalized.
     *
     * @param localeFile absolute locale YAML file path that contained the {@code !file} tag
     * @param referencedPath absolute filesystem path requested by the tag
     * @return the on-disk referenced file path, or an empty result when no fallback file exists
     */
    protected Optional<Path> resolveMissingTaggedFile(Path localeFile, Path referencedPath) {
        return Optional.empty();
    }

    /**
     * Discovers additional locales for a language from sources outside the scanned language
     * directory.
     *
     * <p>Subclasses can override this hook when sibling fallback should consider locales that are
     * not yet present on disk but can be materialized on demand by
     * {@link #resolveMissingLocaleFile(String)}.
     *
     * @param language normalized language identifier such as {@code en}
     * @return additional locale identifiers for the language
     */
    protected List<String> additionalLocalesForLanguage(String language) {
        return List.of();
    }

    protected BaseTranslator createChildTranslator(String path) {
        return new BaseTranslatorNode(this, path);
    }

    protected BaseTranslator childTranslator(String path) {
        this.ensureOpen();
        return this.childTranslators.computeIfAbsent(path, this::createChildTranslator);
    }

    private static ResolvedTranslation adaptResolvedKey(ResolvedTranslation resolved, String key) {
        if (resolved.key().equals(key)) {
            return resolved;
        }
        return new ResolvedTranslation(
                resolved.requestedLocale(),
                key,
                resolved.resolvedLocale(),
                resolved.value(),
                resolved.source()
        );
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
        TreeSet<String> siblings = new TreeSet<>();

        siblings.addAll(this.localeIndex.get().localesForLanguage(language));
        siblings.addAll(this.directoryLocalesForLanguage(language));
        for (String candidate : this.additionalLocalesForLanguage(language)) {
            if (candidate == null) {
                continue;
            }
            try {
                String normalizedCandidate = this.normalizeLocale(candidate);
                if (LocaleSupport.languageOf(normalizedCandidate).equals(language)) {
                    siblings.add(normalizedCandidate);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid locale identifiers supplied by subclasses.
            }
        }

        List<String> orderedSiblings = new ArrayList<>();
        for (String candidate : siblings) {
            if (candidate.equals(locale) || candidate.equals(generalLocale)) {
                continue;
            }
            if (LocaleSupport.isRegional(candidate)) {
                orderedSiblings.add(candidate);
            }
        }
        return orderedSiblings;
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

        Optional<Path> bundlePath = this.resolveBundlePath(locale);
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
            LocaleBundle bundle = YamlLocaleLoader.load(locale, bundlePath.get(), loadVersion, LOGGER, this::resolveMissingTaggedFile);
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
        Map<String, String> placeholders = PlaceholderFlattener.flatten(args);
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
    private Optional<Path> resolveBundlePath(String locale) {
        Optional<Path> indexedPath = this.localeIndex.get().pathFor(locale);
        if (indexedPath.isPresent()) {
            return indexedPath;
        }

        Optional<Path> directoryPath = this.findLocaleFileInDirectory(locale);
        if (directoryPath.isPresent()) {
            this.registerLocalePath(locale, directoryPath.get());
            return directoryPath;
        }

        Optional<Path> materializedPath = this.resolveMissingLocaleFile(locale)
                .map(path -> path.toAbsolutePath().normalize());
        materializedPath.ifPresent(path -> this.registerLocalePath(locale, path));
        return materializedPath;
    }
    private Optional<Path> findLocaleFileInDirectory(String locale) {
        try {
            Files.createDirectories(this.languagesDirectory);
        } catch (IOException exception) {
            LOGGER.warn("Unable to create or access language directory '{}'.", this.languagesDirectory, exception);
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.list(this.languagesDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(BaseRunical::isYamlFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .filter(path -> locale.equals(localeFromFile(path).orElse(null)))
                    .findFirst()
                    .map(path -> path.toAbsolutePath().normalize());
        } catch (IOException exception) {
            LOGGER.warn("Unable to scan language directory '{}' while searching for locale '{}'.", this.languagesDirectory, locale, exception);
            return Optional.empty();
        }
    }
    private List<String> directoryLocalesForLanguage(String language) {
        try {
            Files.createDirectories(this.languagesDirectory);
        } catch (IOException exception) {
            LOGGER.warn("Unable to create or access language directory '{}'.", this.languagesDirectory, exception);
            return List.of();
        }

        try (Stream<Path> stream = Files.list(this.languagesDirectory)) {
            List<String> locales = stream
                    .filter(Files::isRegularFile)
                    .filter(BaseRunical::isYamlFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .map(BaseRunical::localeFromFile)
                    .flatMap(Optional::stream)
                    .filter(locale -> LocaleSupport.languageOf(locale).equals(language))
                    .toList();

            for (String locale : locales) {
                this.findLocaleFileInDirectory(locale).ifPresent(path -> this.registerLocalePath(locale, path));
            }
            return locales;
        } catch (IOException exception) {
            LOGGER.warn("Unable to scan language directory '{}' while collecting locales for language '{}'.", this.languagesDirectory, language, exception);
            return List.of();
        }
    }
    private void registerLocalePath(String locale, Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        this.localeIndex.updateAndGet(index -> index.withLocale(locale, normalizedPath));
    }
    private static Optional<String> localeFromFile(Path path) {
        String fileName = path.getFileName().toString();
        int extensionSeparator = fileName.lastIndexOf('.');
        String rawLocale = extensionSeparator >= 0 ? fileName.substring(0, extensionSeparator) : fileName;
        try {
            return Optional.of(LocaleSupport.normalizeLocale(rawLocale));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
    private static String requireChildPath(String segment) {
        Objects.requireNonNull(segment, "segment");
        return requirePathSegment(segment, "Translator child segment");
    }
    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Translation key must not be blank.");
        }
        return key;
    }
    private static String requirePathSegment(String segment, String label) {
        String normalizedSegment = segment.trim();
        if (normalizedSegment.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        if (normalizedSegment.indexOf('.') >= 0) {
            throw new IllegalArgumentException(label + " must not contain dots.");
        }
        return normalizedSegment;
    }
    private static String normalizeTranslatorPath(String path, String argumentName) {
        Objects.requireNonNull(path, argumentName);
        String normalizedPath = path.trim();
        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException(argumentName + " must not be blank.");
        }

        String[] rawSegments = normalizedPath.split("\\.", -1);
        String[] normalizedSegments = new String[rawSegments.length];
        for (int index = 0; index < rawSegments.length; index++) {
            normalizedSegments[index] = requirePathSegment(rawSegments[index], "Translator path segment");
        }
        return String.join(".", normalizedSegments);
    }
    static boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + ".") || right.startsWith(left + ".");
    }
    private static String requireMountedTranslatorPath(BaseTranslator translator, String argumentName) {
        String path = Objects.requireNonNull(translator, argumentName).getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(argumentName + " must expose a non-root path.");
        }
        return path;
    }
    private static String requireVisibleTranslatorPath(BaseTranslator translator, String argumentName) {
        String path = Objects.requireNonNull(translator, argumentName).getPath();
        if (path == null) {
            throw new IllegalArgumentException(argumentName + " must expose a queryable path.");
        }
        return path;
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
