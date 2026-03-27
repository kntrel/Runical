package com.kntrel.mc.runical.core.placeholder;

import java.util.Objects;

/**
 * Named placeholder used when rendering a resolved translation.
 *
 * <p>Placeholder names match tokens inside braces such as {@code {player}}. Values are converted
 * to strings with {@link String#valueOf(Object)} during rendering. When the value is a
 * {@link BundledPlaceholder}, its default value is exposed through the root token such as
 * {@code {person}}, while its entries are exposed as dotted tokens such as {@code {person.name}}.
 * {@link Translatable Translatable} objects are projected into bundled placeholders reflectively.
 *
 * @param name placeholder token name
 * @param value placeholder value, which may be {@code null}
 */
public record Placeholder(String name, Object value) {

    public Placeholder {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Placeholder name must not be blank.");
        }
    }

    /**
     * Convenience factory for creating a placeholder.
     *
     * @param name placeholder token name
     * @param value placeholder value, which may be {@code null}
     * @return a new placeholder
     */
    public static Placeholder of(String name, Object value) {
        return new Placeholder(name, value);
    }
}
