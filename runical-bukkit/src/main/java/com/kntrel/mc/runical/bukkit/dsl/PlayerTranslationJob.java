package com.kntrel.mc.runical.bukkit.dsl;

import com.kntrel.mc.runical.core.argument.Argument;
import java.util.Arrays;
import java.util.Collection;

/**
 * Player-bound Bukkit translation job.
 */
public interface PlayerTranslationJob extends TranslationJob, PlayerTerminalTranslationJob {

    @Override
    default PlayerTranslationJob arguments(Argument... arguments) {
        return this.arguments(Arrays.asList(arguments));
    }

    @Override
    PlayerTranslationJob arguments(Collection<Argument> arguments);

    @Override
    default PlayerTranslationJob argument(Argument argument) {
        return this.arguments(argument);
    }

    @Override
    default PlayerTranslationJob argument(String name, Object value) {
        return this.argument(new Argument(name, value));
    }

    @Override
    PlayerTerminalTranslationJob orKey();

    @Override
    PlayerTerminalTranslationJob orNull();

    @Override
    PlayerTerminalTranslationJob orDefault(String defaultValue);
}
