package com.kntrel.mc.runical.bukkit.dsl;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Synchronous terminal operations for Bukkit translation jobs.
 */
public interface TerminalTranslationJob extends com.kntrel.mc.runical.core.dsl.TerminalTranslationJob {

    /**
     * Resolves the translation into a compiled component according to the configured miss policy.
     *
     * @return the compiled component, or {@code null} when the active miss policy resolves to no
     *         message
     */
    BaseComponent component();

    /**
     * Resolves, compiles, and schedules the message to be sent to the supplied player.
     *
     * @param player recipient player
     * @param endpoint message endpoint that should receive the component
     * @return a future completing with whether the message was delivered
     */
    CompletableFuture<Boolean> send(Player player, ChatMessageType endpoint);

    /**
     * Resolves, compiles, and schedules the message to be sent to the supplied player's chat.
     *
     * @param player recipient player
     * @return a future completing with whether the message was delivered
     */
    default CompletableFuture<Boolean> send(Player player) {
        return this.send(player, ChatMessageType.CHAT);
    }

    /**
     * Resolves, compiles, and schedules the message to be sent to the supplied player's action
     * bar.
     *
     * @param player recipient player
     * @return a future completing with whether the message was delivered
     */
    default CompletableFuture<Boolean> sendActionBar(Player player) {
        return this.send(player, ChatMessageType.ACTION_BAR);
    }

    @Override
    AsyncTerminalTranslationJob async();
}
