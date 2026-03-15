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
    public final String translate(String locale, String key, Placeholder... args) {
        return this.resolve(locale, key, args).orKey();
    }
    public final String translate(String locale, String key) {
        return translate(locale, key, new Placeholder[0]);
    }
    public final CompletableFuture<String> translateAsync(String locale, String key, Placeholder... args) {
        return this.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::orKey);
    }
    public final CompletableFuture<String> translateAsync(String locale, String key) {
        return this.translateAsync(locale, key, new Placeholder[0]);
    }
    public final String translateOrNull(String locale, String key, Placeholder... args) {
        return this.resolve(locale, key, args).value();
    }
    public final String translateOrNull(String locale, String key) {
        return this.translateOrNull(locale, key, new Placeholder[0]);
    }
    public final CompletableFuture<String> translateOrNullAsync(String locale, String key, Placeholder... args) {
        return this.resolveAsync(locale, key, args).thenApply(ResolvedTranslation::value);
    }
    public final CompletableFuture<String> translateOrNullAsync(String locale, String key) {
        return this.translateOrNullAsync(locale, key, new Placeholder[0]);
    }
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
    public final ResolvedTranslation resolve(String locale, String key) {
        return resolve(locale, key, new Placeholder[0]);
    }
    public final CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key, Placeholder... args) {
        ensureOpen();
        return CompletableFuture.supplyAsync(() -> resolve(locale, key, args), this.asyncExecutor);
    }
    public final CompletableFuture<ResolvedTranslation> resolveAsync(String locale, String key) {
        return resolveAsync(locale, key, new Placeholder[0]);
    }
    public final String formatList(String locale, Collection<?> items) {
        return formatList(locale, items, ListStyle.AND);
    }

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
    public final String getDefaultLocale() {
        return this.defaultLocale.get();
    }
    public final void setDefaultLocale(String locale) {
        this.defaultLocale.set(this.normalizeLocale(locale));
    }
    public final boolean hasLocale(String locale) {
        ensureOpen();
        return this.localeIndex.get().hasLocale(locale);
    }
    public final boolean isLoaded(String locale) {
        ensureOpen();
        return this.loadedLocales.containsKey(this.normalizeLocale(locale));
    }
    public final void preload(String locale) {
        ensureOpen();
        String normalizedLocale = this.normalizeLocale(locale);
        long accessSequence = this.querySequence.incrementAndGet();
        touchOrLoadBundle(normalizedLocale, accessSequence);
        cleanupIfNeeded(accessSequence);
    }
    public final void reload() {
        ensureOpenOrFresh();
        this.indexVersion.incrementAndGet();
        this.localeIndex.set(LocaleIndex.scan(this.languagesDirectory, LOGGER));
        this.loadedLocales.clear();
        this.inFlightLoads.clear();
    }
    public final void evict(String locale) {
        ensureOpen();
        this.loadedLocales.remove(this.normalizeLocale(locale));
    }
    public final void evictAll() {
        ensureOpen();
        this.loadedLocales.clear();
    }
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
    protected final String normalizeLocale(String locale) {
        return LocaleSupport.normalizeLocale(locale);
    }
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
        enforceLoadedLocaleLimit();
        evictIdleLocales(accessSequence);
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
