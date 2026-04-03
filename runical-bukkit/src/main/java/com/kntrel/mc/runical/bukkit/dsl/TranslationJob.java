package com.kntrel.mc.runical.bukkit.dsl;

import com.kntrel.mc.runical.core.argument.Argument;

/**
 * Mutable Bukkit translation DSL that adds component and send terminals on top of the core job.
 */
public interface TranslationJob extends com.kntrel.mc.runical.core.dsl.TranslationJob, TerminalTranslationJob {

    @Override
    TranslationJob argument(Argument argument);

    @Override
    default TranslationJob argument(String name, Object value) {
        return this.argument(new Argument(name, value));
    }

    @Override
    TerminalTranslationJob orKey();

    @Override
    TerminalTranslationJob orNull();

    @Override
    TerminalTranslationJob orDefault(String defaultValue);

    @Override
    AsyncTranslationJob async();
}
