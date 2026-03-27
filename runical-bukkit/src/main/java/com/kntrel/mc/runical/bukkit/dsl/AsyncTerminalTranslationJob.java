package com.kntrel.mc.runical.bukkit.dsl;

import net.md_5.bungee.api.chat.BaseComponent;

import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous terminal operations for Bukkit translation jobs.
 */
public interface AsyncTerminalTranslationJob extends com.kntrel.mc.runical.core.dsl.AsyncTerminalTranslationJob {

    /**
     * Resolves the translation into a compiled component according to the configured miss policy.
     *
     * @return a future completing with the compiled component, or {@code null} when the active
     *         miss policy resolves to no message
     */
    CompletableFuture<BaseComponent> component();
}
