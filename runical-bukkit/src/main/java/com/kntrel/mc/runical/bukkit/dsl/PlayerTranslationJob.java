package com.kntrel.mc.runical.bukkit.dsl;

import com.kntrel.mc.runical.core.argument.Argument;

/**
 * Player-bound Bukkit translation job.
 */
public interface PlayerTranslationJob extends TranslationJob, PlayerTerminalTranslationJob {

    @Override
    PlayerTranslationJob argument(Argument argument);

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
