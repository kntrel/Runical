package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.dsl.AsyncTranslationJob;
import com.kntrel.mc.runical.core.dsl.TerminalTranslationJob;
import com.kntrel.mc.runical.core.dsl.TranslationJob;
import com.kntrel.mc.runical.core.placeholder.Placeholder;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Shared mutable translation job state and terminal behavior.
 */
public abstract class BaseTranslationJob implements TranslationJob {

    private static final Placeholder[] EMPTY_ARGS = new Placeholder[0];

    private final Placeholder[] args_;
    private MissPolicy missPolicy_ = MissPolicy.KEY;
    private String defaultValue_;
    private AsyncTranslationJob asyncTerminal_;

    protected BaseTranslationJob(Placeholder... args) {
        this.args_ = args == null ? EMPTY_ARGS : args;
    }

    @Override
    public TerminalTranslationJob orKey() {
        this.missPolicy_ = MissPolicy.KEY;
        this.defaultValue_ = null;
        return this;
    }

    @Override
    public TerminalTranslationJob orNull() {
        this.missPolicy_ = MissPolicy.NULL;
        this.defaultValue_ = null;
        return this;
    }

    @Override
    public TerminalTranslationJob orDefault(String defaultValue) {
        this.missPolicy_ = MissPolicy.DEFAULT;
        this.defaultValue_ = Objects.requireNonNull(defaultValue, "defaultValue");
        return this;
    }

    @Override
    public final String message() {
        TerminalSnapshot snapshot = this.snapshot();
        return this.materializeMessage(this.resolveRaw(), snapshot);
    }

    @Override
    public final ResolvedTranslation translation() {
        return this.resolveRaw();
    }

    @Override
    public AsyncTranslationJob async() {
        if (this.asyncTerminal_ == null) {
            this.asyncTerminal_ = this.createAsyncTerminal();
        }
        return this.asyncTerminal_;
    }

    protected final Placeholder[] args() {
        return this.args_;
    }

    protected final TerminalSnapshot snapshot() {
        return new TerminalSnapshot(this.missPolicy_, this.defaultValue_);
    }

    protected final String materializeMessage(ResolvedTranslation resolved, TerminalSnapshot snapshot) {
        return switch (snapshot.missPolicy()) {
            case KEY -> resolved.orKey();
            case NULL -> resolved.value();
            case DEFAULT -> resolved.found() ? resolved.value() : this.renderDefaultValue(snapshot.defaultValue());
        };
    }

    protected AsyncTranslationJob createAsyncTerminal() {
        return new AsyncView();
    }

    protected abstract ResolvedTranslation resolveRaw();

    protected abstract CompletableFuture<ResolvedTranslation> resolveRawAsync();

    protected abstract String renderDefaultValue(String defaultValue);

    protected enum MissPolicy {
        KEY,
        NULL,
        DEFAULT
    }

    protected record TerminalSnapshot(MissPolicy missPolicy, String defaultValue) {
    }

    private final class AsyncView implements AsyncTranslationJob {

        @Override
        public CompletableFuture<String> message() {
            TerminalSnapshot snapshot = BaseTranslationJob.this.snapshot();
            return BaseTranslationJob.this.resolveRawAsync()
                    .thenApply(resolved -> BaseTranslationJob.this.materializeMessage(resolved, snapshot));
        }

        @Override
        public CompletableFuture<ResolvedTranslation> translation() {
            return BaseTranslationJob.this.resolveRawAsync();
        }
    }
}
