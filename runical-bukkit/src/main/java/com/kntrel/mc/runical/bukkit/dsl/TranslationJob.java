package com.kntrel.mc.runical.bukkit.dsl;

import com.kntrel.mc.runical.core.argument.Argument;
import java.util.Arrays;
import java.util.Collection;

/**
 * Mutable Bukkit translation DSL that adds component and send terminals on top of the core job.
 */
public interface TranslationJob extends com.kntrel.mc.runical.core.dsl.TranslationJob, TerminalTranslationJob {

    @Override
    default TranslationJob arguments(Argument... arguments) {
        return this.arguments(Arrays.asList(arguments));
    }

    @Override
    TranslationJob arguments(Collection<Argument> arguments);

    @Override
    default TranslationJob argument(Argument argument) {
        return this.arguments(argument);
    }

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
