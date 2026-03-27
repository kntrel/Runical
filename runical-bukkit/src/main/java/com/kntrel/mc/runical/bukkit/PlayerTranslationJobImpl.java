package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.ResolvedTranslation;
import com.kntrel.mc.runical.core.placeholder.Placeholder;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTerminalTranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTranslationJob;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class PlayerTranslationJobImpl extends BaseBukkitTranslationJob implements PlayerTranslationJob {

    private final Player player_;

    PlayerTranslationJobImpl(Runical root, Player player, String key, Placeholder... args) {
        super(root, key, args);
        this.player_ = Objects.requireNonNull(player, "player");
    }

    @Override
    public PlayerTerminalTranslationJob orKey() {
        super.orKey();
        return this;
    }

    @Override
    public PlayerTerminalTranslationJob orNull() {
        super.orNull();
        return this;
    }

    @Override
    public PlayerTerminalTranslationJob orDefault(String defaultValue) {
        super.orDefault(defaultValue);
        return this;
    }

    @Override
    public CompletableFuture<Boolean> send(ChatMessageType endpoint) {
        return this.send(this.player_, endpoint);
    }

    @Override
    protected ResolvedTranslation resolveRaw() {
        return this.root().resolvePlayerTranslation(this.player_, this.key(), this.args());
    }

    @Override
    protected CompletableFuture<ResolvedTranslation> resolveRawAsync() {
        return this.root().resolvePlayerTranslationAsync(this.player_, this.key(), this.args());
    }

    @Override
    protected String renderDefaultValue(String defaultValue) {
        return this.root().renderLocaleMessage(defaultValue, this.args());
    }
}
