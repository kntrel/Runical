package com.kntrel.mc.runical.core;

import java.util.Objects;

/**
 * Named placeholder used when rendering a resolved translation.
 *
 * <p>Placeholder names match tokens inside braces such as {@code {player}}. Values are converted
 * to strings with {@link String#valueOf(Object)} during rendering.
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
