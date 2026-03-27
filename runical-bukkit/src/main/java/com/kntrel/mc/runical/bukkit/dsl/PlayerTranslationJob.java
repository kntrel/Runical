package com.kntrel.mc.runical.bukkit.dsl;

/**
 * Player-bound Bukkit translation job.
 */
public interface PlayerTranslationJob extends TranslationJob, PlayerTerminalTranslationJob {

    @Override
    PlayerTerminalTranslationJob orKey();

    @Override
    PlayerTerminalTranslationJob orNull();

    @Override
    PlayerTerminalTranslationJob orDefault(String defaultValue);
}
