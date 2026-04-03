package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.dsl.AsyncTranslationJob;
import com.kntrel.mc.runical.core.dsl.TerminalTranslationJob;
import com.kntrel.mc.runical.core.dsl.TranslationJob;
import com.kntrel.mc.runical.core.argument.Argument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Shared mutable translation job state and terminal behavior.
 */
public abstract class BaseTranslationJob implements TranslationJob {

    private static final Argument[] EMPTY_ARGS = new Argument[0];

    private final ArrayList<Argument> args_;
    private MissPolicy missPolicy_ = MissPolicy.KEY;
    private String defaultValue_;
    private AsyncTranslationJob asyncTerminal_;

    protected BaseTranslationJob() {
        this.args_ = new ArrayList<>();
    }

    @Override
    public TranslationJob arguments(Collection<Argument> arguments) {
        this.args_.addAll(arguments);
        return this;
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
        return this.materializeMessage(this.resolveRaw(snapshot.args()), snapshot);
    }

    @Override
    public final ResolvedTranslation translation() {
        return this.resolveRaw(this.args());
    }

    @Override
    public AsyncTranslationJob async() {
        if (this.asyncTerminal_ == null) {
            this.asyncTerminal_ = this.createAsyncTerminal();
        }
        return this.asyncTerminal_;
    }

    protected final Argument[] args() {
        return this.args_.isEmpty() ? EMPTY_ARGS : this.args_.toArray(EMPTY_ARGS);
    }

    protected final TerminalSnapshot snapshot() {
        return new TerminalSnapshot(this.missPolicy_, this.defaultValue_, this.args());
    }

    protected final String materializeMessage(ResolvedTranslation resolved, TerminalSnapshot snapshot) {
        return switch (snapshot.missPolicy()) {
            case KEY -> resolved.orKey();
            case NULL -> resolved.value();
            case DEFAULT -> resolved.found()
                    ? resolved.value()
                    : this.renderDefaultValue(snapshot.defaultValue(), snapshot.args());
        };
    }

    protected AsyncTranslationJob createAsyncTerminal() {
        return new AsyncView();
    }

    protected abstract ResolvedTranslation resolveRaw(Argument[] args);

    protected abstract CompletableFuture<ResolvedTranslation> resolveRawAsync(Argument[] args);

    protected abstract String renderDefaultValue(String defaultValue, Argument[] args);

    protected enum MissPolicy {
        KEY,
        NULL,
        DEFAULT
    }

    protected record TerminalSnapshot(MissPolicy missPolicy, String defaultValue, Argument[] args) {
    }

    private final class AsyncView implements AsyncTranslationJob {

        @Override
        public CompletableFuture<String> message() {
            TerminalSnapshot snapshot = BaseTranslationJob.this.snapshot();
            return BaseTranslationJob.this.resolveRawAsync(snapshot.args())
                    .thenApply(resolved -> BaseTranslationJob.this.materializeMessage(resolved, snapshot));
        }

        @Override
        public CompletableFuture<ResolvedTranslation> translation() {
            return BaseTranslationJob.this.resolveRawAsync(BaseTranslationJob.this.args());
        }
    }
}
