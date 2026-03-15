package com.kntrel.mc.runical.core;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

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

    public static Builder builder() {
        return new Builder();
    }

    public String defaultLocale() {
        return this.defaultLocale;
    }

    public long idleQueryThreshold() {
        return this.idleQueryThreshold;
    }

    public long cleanupIntervalQueries() {
        return this.cleanupIntervalQueries;
    }

    public int maxLoadedLocales() {
        return this.maxLoadedLocales;
    }

    public Executor asyncExecutor() {
        return this.asyncExecutor;
    }

    public boolean shutdownAsyncExecutorOnClose() {
        return this.shutdownAsyncExecutorOnClose;
    }

    public static final class Builder {
        private String defaultLocale = "en-us";
        private long idleQueryThreshold = 500L;
        private long cleanupIntervalQueries = 50L;
        private int maxLoadedLocales = 32;
        private Executor asyncExecutor;
        private boolean shutdownAsyncExecutorOnClose;

        private Builder() {
        }

        public Builder defaultLocale(String defaultLocale) {
            this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
            return this;
        }

        public Builder idleQueryThreshold(long idleQueryThreshold) {
            if (idleQueryThreshold < 0L) {
                throw new IllegalArgumentException("idleQueryThreshold must be >= 0.");
            }
            this.idleQueryThreshold = idleQueryThreshold;
            return this;
        }

        public Builder cleanupIntervalQueries(long cleanupIntervalQueries) {
            if (cleanupIntervalQueries <= 0L) {
                throw new IllegalArgumentException("cleanupIntervalQueries must be > 0.");
            }
            this.cleanupIntervalQueries = cleanupIntervalQueries;
            return this;
        }

        public Builder maxLoadedLocales(int maxLoadedLocales) {
            if (maxLoadedLocales < 0) {
                throw new IllegalArgumentException("maxLoadedLocales must be >= 0.");
            }
            this.maxLoadedLocales = maxLoadedLocales;
            return this;
        }

        public Builder asyncExecutor(Executor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;
            return this;
        }

        public Builder shutdownAsyncExecutorOnClose(boolean shutdownAsyncExecutorOnClose) {
            this.shutdownAsyncExecutorOnClose = shutdownAsyncExecutorOnClose;
            return this;
        }

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
