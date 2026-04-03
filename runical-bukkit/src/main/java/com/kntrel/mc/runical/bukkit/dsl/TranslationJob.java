package com.kntrel.mc.runical.bukkit.dsl;

/**
 * Mutable Bukkit translation DSL that adds component and send terminals on top of the core job.
 */
public interface TranslationJob extends com.kntrel.mc.runical.core.dsl.TranslationJob, TerminalTranslationJob {

    @Override
    TerminalTranslationJob orKey();

    @Override
    TerminalTranslationJob orNull();

    @Override
    TerminalTranslationJob orDefault(String defaultValue);

    @Override
    AsyncTranslationJob async();
}
