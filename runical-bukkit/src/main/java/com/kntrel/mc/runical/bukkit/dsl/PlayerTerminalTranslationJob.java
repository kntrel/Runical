package com.kntrel.mc.runical.bukkit.dsl;

import net.md_5.bungee.api.ChatMessageType;

import java.util.concurrent.CompletableFuture;

/**
 * Terminal surface for a player-bound Bukkit translation job.
 */
public interface PlayerTerminalTranslationJob extends TerminalTranslationJob {

    /**
     * Resolves, compiles, and schedules the message to be sent to the bound player's selected
     * endpoint.
     *
     * @param endpoint message endpoint that should receive the component
     * @return a future completing with whether the message was delivered
     */
    CompletableFuture<Boolean> send(ChatMessageType endpoint);

    /**
     * Resolves, compiles, and schedules the message to be sent to the bound player's chat.
     *
     * @return a future completing with whether the message was delivered
     */
    default CompletableFuture<Boolean> send() {
        return this.send(ChatMessageType.CHAT);
    }

    /**
     * Resolves, compiles, and schedules the message to be sent to the bound player's action bar.
     *
     * @return a future completing with whether the message was delivered
     */
    default CompletableFuture<Boolean> sendActionBar() {
        return this.send(ChatMessageType.ACTION_BAR);
    }
}
