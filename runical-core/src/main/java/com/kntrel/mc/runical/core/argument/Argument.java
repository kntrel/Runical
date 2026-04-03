package com.kntrel.mc.runical.core.argument;

import java.util.Objects;

/**
 * Named translation argument used when rendering a resolved translation.
 *
 * <p>Argument names match placeholder tokens inside braces such as {@code {player}}. Values are
 * converted to strings with {@link String#valueOf(Object)} during rendering. When the value is a
 * {@link BundledArgument}, its default value is exposed through the root token such as
 * {@code {person}}, while its entries are exposed as dotted tokens such as {@code {person.name}}.
 * {@link Translatable Translatable} objects are projected into bundled placeholders reflectively.
 *
 * @param name placeholder token name
 * @param value argument value, which may be {@code null}
 */
public record Argument(String name, Object value) {

    public Argument {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Placeholder name must not be blank.");
        }
    }

    /**
     * Convenience factory for creating a translation argument.
     *
     * @param name placeholder token name
     * @param value argument value, which may be {@code null}
     * @return a new argument
     */
    public static Argument of(String name, Object value) {
        return new Argument(name, value);
    }
}
