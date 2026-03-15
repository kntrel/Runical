package com.kntrel.mc.runical.core;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Immutable configuration for {@link BaseRunical}.
 *
 * <p>Use {@link #builder()} to create an instance. The default configuration uses
 * {@code en-us} as the fallback locale, evicts idle locales after 500 queries, runs cache cleanup
 * every 50 queries, keeps up to 32 locale bundles loaded, and creates a virtual-thread-per-task
 * async executor when none is supplied.
 */
public final class RunicalOptions {

    private final String defaultLocale;
    private final long idleQueryThreshold;
    private final long cleanupIntervalQueries;
    private final int maxLoadedLocales;
    private final Executor asyncExecutor;
    private final boolean shutdownAsyncExecutorOnClose;

    private RunicalOptions(Builder builder) {
        this.defaultLocale = normalizeDefaultLocale(builder.defaultLocale);
        this.idleQueryThreshold = builder.idleQueryThreshold;
        this.cleanupIntervalQueries = builder.cleanupIntervalQueries;
        this.maxLoadedLocales = builder.maxLoadedLocales;
        this.asyncExecutor = builder.asyncExecutor;
        this.shutdownAsyncExecutorOnClose = builder.shutdownAsyncExecutorOnClose;
    }

    /**
     * Creates a new builder initialized with the default option values.
     *
     * @return a new options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the normalized default locale used as the final fallback for lookups.
     *
     * @return the default locale
     */
    public String defaultLocale() {
        return this.defaultLocale;
    }

    /**
     * Returns the number of queries a cached locale may remain unused before cleanup may evict it.
     *
     * <p>A value of {@code 0} disables idle eviction.
     *
     * @return the idle eviction threshold in queries
     */
    public long idleQueryThreshold() {
        return this.idleQueryThreshold;
    }

    /**
     * Returns how often cache cleanup runs, measured in completed queries.
     *
     * @return the cleanup interval in queries
     */
    public long cleanupIntervalQueries() {
        return this.cleanupIntervalQueries;
    }

    /**
     * Returns the maximum number of locale bundles to keep in memory.
     *
     * <p>A value of {@code 0} disables the cache size limit.
     *
     * @return the maximum number of loaded locales
     */
    public int maxLoadedLocales() {
        return this.maxLoadedLocales;
    }

    /**
     * Returns the executor used by async lookup methods.
     *
     * <p>When this is {@code null}, {@link BaseRunical} creates its own virtual-thread-per-task
     * executor.
     *
     * @return the configured async executor, or {@code null}
     */
    public Executor asyncExecutor() {
        return this.asyncExecutor;
    }

    /**
     * Returns whether {@link BaseRunical#close()} should shut down the async executor.
     *
     * <p>This applies only when the executor is an {@link java.util.concurrent.ExecutorService}.
     *
     * @return {@code true} when the executor should be shut down on close
     */
    public boolean shutdownAsyncExecutorOnClose() {
        return this.shutdownAsyncExecutorOnClose;
    }

    /**
     * Mutable builder for {@link RunicalOptions}.
     */
    public static final class Builder {
        private String defaultLocale = "en-us";
        private long idleQueryThreshold = 500L;
        private long cleanupIntervalQueries = 50L;
        private int maxLoadedLocales = 32;
        private Executor asyncExecutor;
        private boolean shutdownAsyncExecutorOnClose;

        private Builder() {
        }

        /**
         * Sets the default locale used as the final fallback for translations and list formats.
         *
         * @param defaultLocale default locale identifier
         * @return this builder
         * @throws NullPointerException if {@code defaultLocale} is {@code null}
         * @throws IllegalArgumentException if {@code defaultLocale} is blank
         */
        public Builder defaultLocale(String defaultLocale) {
            this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
            return this;
        }

        /**
         * Sets the number of queries a cached locale may remain idle before cleanup may evict it.
         *
         * <p>Use {@code 0} to disable idle eviction.
         *
         * @param idleQueryThreshold idle eviction threshold in queries
         * @return this builder
         * @throws IllegalArgumentException if {@code idleQueryThreshold} is negative
         */
        public Builder idleQueryThreshold(long idleQueryThreshold) {
            if (idleQueryThreshold < 0L) {
                throw new IllegalArgumentException("idleQueryThreshold must be >= 0.");
            }
            this.idleQueryThreshold = idleQueryThreshold;
            return this;
        }

        /**
         * Sets how often cache cleanup runs, measured in completed queries.
         *
         * @param cleanupIntervalQueries cleanup interval in queries
         * @return this builder
         * @throws IllegalArgumentException if {@code cleanupIntervalQueries} is not positive
         */
        public Builder cleanupIntervalQueries(long cleanupIntervalQueries) {
            if (cleanupIntervalQueries <= 0L) {
                throw new IllegalArgumentException("cleanupIntervalQueries must be > 0.");
            }
            this.cleanupIntervalQueries = cleanupIntervalQueries;
            return this;
        }

        /**
         * Sets the maximum number of locale bundles to keep loaded at once.
         *
         * <p>Use {@code 0} to disable the cache size limit.
         *
         * @param maxLoadedLocales maximum number of cached locales
         * @return this builder
         * @throws IllegalArgumentException if {@code maxLoadedLocales} is negative
         */
        public Builder maxLoadedLocales(int maxLoadedLocales) {
            if (maxLoadedLocales < 0) {
                throw new IllegalArgumentException("maxLoadedLocales must be >= 0.");
            }
            this.maxLoadedLocales = maxLoadedLocales;
            return this;
        }

        /**
         * Sets the executor used by async lookup methods.
         *
         * <p>When omitted, {@link BaseRunical} creates a virtual-thread-per-task executor.
         *
         * @param asyncExecutor executor to use, or {@code null} to use Runical's default
         * @return this builder
         */
        public Builder asyncExecutor(Executor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;
            return this;
        }

        /**
         * Sets whether {@link BaseRunical#close()} should shut down the configured async executor.
         *
         * <p>This is ignored when no custom executor is supplied.
         *
         * @param shutdownAsyncExecutorOnClose whether to shut down the executor on close
         * @return this builder
         */
        public Builder shutdownAsyncExecutorOnClose(boolean shutdownAsyncExecutorOnClose) {
            this.shutdownAsyncExecutorOnClose = shutdownAsyncExecutorOnClose;
            return this;
        }

        /**
         * Builds an immutable {@link RunicalOptions} instance.
         *
         * @return the configured options
         * @throws IllegalArgumentException if the default locale is blank
         */
        public RunicalOptions build() {
            return new RunicalOptions(this);
        }
    }

    private static String normalizeDefaultLocale(String locale) {
        if (locale.isBlank()) {
            throw new IllegalArgumentException("defaultLocale must not be blank.");
        }
        return locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
