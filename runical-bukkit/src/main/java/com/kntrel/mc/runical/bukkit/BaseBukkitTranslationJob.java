package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.BaseTranslationJob;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import com.kntrel.mc.runical.bukkit.dsl.AsyncTranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.TerminalTranslationJob;
import com.kntrel.mc.runical.bukkit.dsl.TranslationJob;
import com.kntrel.mc.runical.core.argument.Argument;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

abstract class BaseBukkitTranslationJob extends BaseTranslationJob implements TranslationJob {

    private final Runical root_;
    private final String key_;

    BaseBukkitTranslationJob(Runical root, String key) {
        super();
        this.root_ = Objects.requireNonNull(root, "root");
        this.key_ = Objects.requireNonNull(key, "key");
    }

    @Override
    public TranslationJob arguments(Collection<Argument> arguments) {
        super.arguments(arguments);
        return this;
    }

    @Override
    public TerminalTranslationJob orKey() {
        super.orKey();
        return this;
    }

    @Override
    public TerminalTranslationJob orNull() {
        super.orNull();
        return this;
    }

    @Override
    public TerminalTranslationJob orDefault(String defaultValue) {
        super.orDefault(defaultValue);
        return this;
    }

    @Override
    public BaseComponent component() {
        TerminalSnapshot snapshot = this.snapshot();
        return this.compileComponent(this.resolveRaw(snapshot.args()), snapshot);
    }

    @Override
    public CompletableFuture<Boolean> send(Player player, ChatMessageType endpoint) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(endpoint, "endpoint");
        TerminalSnapshot snapshot = this.snapshot();
        CompletableFuture<BaseComponent> messageFuture = this.resolveRawAsync(snapshot.args())
                .thenApply(resolved -> this.compileComponent(resolved, snapshot));
        return this.root_.sendMessage(player, endpoint, messageFuture);
    }

    @Override
    public AsyncTranslationJob async() {
        return (AsyncTranslationJob) super.async();
    }

    @Override
    protected AsyncTranslationJob createAsyncTerminal() {
        return new AsyncView();
    }

    protected final Runical root() {
        return this.root_;
    }

    protected final String key() {
        return this.key_;
    }

    private BaseComponent compileComponent(ResolvedTranslation resolved, TerminalSnapshot snapshot) {
        String message = this.materializeMessage(resolved, snapshot);
        return message == null ? null : ComponentMarkupCompiler.compile(message);
    }

    private final class AsyncView implements AsyncTranslationJob {

        @Override
        public CompletableFuture<String> message() {
            TerminalSnapshot snapshot = BaseBukkitTranslationJob.this.snapshot();
            return BaseBukkitTranslationJob.this.resolveRawAsync(snapshot.args())
                    .thenApply(resolved -> BaseBukkitTranslationJob.this.materializeMessage(resolved, snapshot));
        }

        @Override
        public CompletableFuture<ResolvedTranslation> translation() {
            return BaseBukkitTranslationJob.this.resolveRawAsync(BaseBukkitTranslationJob.this.args());
        }

        @Override
        public CompletableFuture<BaseComponent> component() {
            TerminalSnapshot snapshot = BaseBukkitTranslationJob.this.snapshot();
            return BaseBukkitTranslationJob.this.resolveRawAsync(snapshot.args())
                    .thenApply(resolved -> BaseBukkitTranslationJob.this.compileComponent(resolved, snapshot));
        }
    }
}
