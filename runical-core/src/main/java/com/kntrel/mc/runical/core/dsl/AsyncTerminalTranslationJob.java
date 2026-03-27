package com.kntrel.mc.runical.core.dsl;

import com.kntrel.mc.runical.core.ResolvedTranslation;

import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous terminal operations for a translation job.
 */
public interface AsyncTerminalTranslationJob {

    /**
     * Resolves the translation into a rendered message according to the configured miss policy.
     *
     * @return a future completing with the rendered message, unresolved key, configured default
     *         value, or {@code null} depending on the active miss policy
     */
    CompletableFuture<String> message();

    /**
     * Resolves the translation and returns the raw lookup result.
     *
     * <p>This terminal ignores the configured miss policy and always returns the original
     * {@link ResolvedTranslation}.
     *
     * @return a future completing with the raw lookup result
     */
    CompletableFuture<ResolvedTranslation> translation();
}
