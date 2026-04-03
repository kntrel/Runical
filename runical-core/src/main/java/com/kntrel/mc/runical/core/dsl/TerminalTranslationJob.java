package com.kntrel.mc.runical.core.dsl;

/**
 * Synchronous terminal operations for a translation job.
 */
public interface TerminalTranslationJob {

    /**
     * Resolves the translation into a rendered message according to the configured miss policy.
     *
     * @return the rendered message, the unresolved key, a configured default value, or
     *         {@code null} depending on the active miss policy
     */
    String message();

    /**
     * Switches to the asynchronous terminal surface for this job.
     *
     * @return asynchronous terminal operations backed by the same mutable job state
     */
    AsyncTerminalTranslationJob async();
}
