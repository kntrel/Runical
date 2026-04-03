package com.kntrel.mc.runical.core.dsl;

import com.kntrel.mc.runical.core.ResolvedTranslation;

import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous raw and rendered terminal operations for a translation job before miss policy is
 * fixed.
 */
public interface AsyncTranslationJob extends AsyncTerminalTranslationJob {

    /**
     * Resolves the translation and returns the raw lookup result.
     *
     * @return a future completing with the raw lookup result
     */
    CompletableFuture<ResolvedTranslation> translation();
}
