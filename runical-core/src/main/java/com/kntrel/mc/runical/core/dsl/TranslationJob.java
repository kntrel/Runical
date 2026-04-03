package com.kntrel.mc.runical.core.dsl;

import com.kntrel.mc.runical.core.ResolvedTranslation;
import com.kntrel.mc.runical.core.argument.Argument;
import java.util.Arrays;
import java.util.Collection;

/**
 * Mutable translation DSL that accumulates miss-handling intent until a terminal operation is
 * invoked.
 */
public interface TranslationJob extends TerminalTranslationJob {

    /**
     * Adds translation arguments to the job.
     *
     * @param arguments the arguments
     * @return this job
     */
    default TranslationJob arguments(Argument... arguments) {
        return this.arguments(Arrays.asList(arguments));
    }

    /**
     * Adds translation arguments to the job.
     *
     * @param arguments the arguments
     * @return this job
     */
    TranslationJob arguments(Collection<Argument> arguments);

    /**
     * Adds a translation argument to the job, to be resolved and rendered on top of a placeholder of the same name.
     *
     * @param argument the argument
     * @return this job
     */
    default TranslationJob argument(Argument argument) {
        return this.arguments(argument);
    }

    /**
     * Adds a named translation argument to the job.
     *
     * @param name placeholder token name
     * @param value value exposed for that token, which may be {@code null}
     * @return this job
     */
    default TranslationJob argument(String name, Object value) {
        return this.argument(new Argument(name, value));
    }

    /**
     * Configures the job to fall back to the unresolved key when the lookup misses.
     *
     * @return the terminal surface with the configured miss policy
     */
    TerminalTranslationJob orKey();

    /**
     * Configures the job to return {@code null} when the lookup misses.
     *
     * @return the terminal surface with the configured miss policy
     */
    TerminalTranslationJob orNull();

    /**
     * Configures the job to render {@code defaultValue} when the lookup misses.
     *
     * @param defaultValue fallback template rendered with the job arguments
     * @return the terminal surface with the configured miss policy
     * @throws NullPointerException if {@code defaultValue} is {@code null}
     */
    TerminalTranslationJob orDefault(String defaultValue);

    /**
     * Resolves the translation and returns the raw lookup result.
     *
     * @return the raw lookup result
     */
    ResolvedTranslation translation();

    @Override
    AsyncTranslationJob async();
}
