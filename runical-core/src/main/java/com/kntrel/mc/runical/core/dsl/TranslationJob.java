package com.kntrel.mc.runical.core.dsl;

import com.kntrel.mc.runical.core.ResolvedTranslation;

/**
 * Mutable translation DSL that accumulates miss-handling intent until a terminal operation is
 * invoked.
 */
public interface TranslationJob extends TerminalTranslationJob {

    /**
     * Configures the job to fall back to the unresolved key when the lookup misses.
     *
     * @return the terminal surface with the configured miss policy
     */
    TerminalTranslationJob orKey();

    /**
     * Configures the job to return {@code null} when the lookup misses.
     *
     * @return the terminal surface with the configured miss policy
     */
    TerminalTranslationJob orNull();

    /**
     * Configures the job to render {@code defaultValue} when the lookup misses.
     *
     * @param defaultValue fallback template rendered with the job placeholders
     * @return the terminal surface with the configured miss policy
     * @throws NullPointerException if {@code defaultValue} is {@code null}
     */
    TerminalTranslationJob orDefault(String defaultValue);

    /**
     * Resolves the translation and returns the raw lookup result.
     *
     * @return the raw lookup result
     */
    ResolvedTranslation translation();

    @Override
    AsyncTranslationJob async();
}
