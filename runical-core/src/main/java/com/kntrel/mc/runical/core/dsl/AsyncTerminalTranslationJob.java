package com.kntrel.mc.runical.core.dsl;

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

}
