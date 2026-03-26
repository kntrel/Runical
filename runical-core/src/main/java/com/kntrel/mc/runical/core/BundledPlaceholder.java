package com.kntrel.mc.runical.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Group of placeholder values that can be mounted under a single placeholder namespace.
 *
 * <p>Bundled placeholders are flattened into dotted placeholder tokens during rendering. For
 * example, passing {@code Placeholder.of("person", bundled)} exposes entries such as
 * {@code {person.name}} and {@code {person.age}}.
 */
public final class BundledPlaceholder {

    private final LinkedHashMap<String, Object> values;

    private BundledPlaceholder(LinkedHashMap<String, Object> values) {
        this.values = values;
    }

    /**
     * Creates a new bundled placeholder containing the first named value.
     *
     * @param name bundled placeholder segment name
     * @param value bundled placeholder value, which may be {@code null}
     * @return a new bundled placeholder
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or contains a dot
     */
    public static BundledPlaceholder of(String name, Object value) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        put(values, name, value);
        return new BundledPlaceholder(values);
    }

    /**
     * Adds a named value to the BundledPlaceHolder.
     *
     * <p>If the bundled placeholder already contains the same segment name, the new value replaces
     * the previous value.
     *
     * @param name bundled placeholder segment name
     * @param value bundled placeholder value, which may be {@code null}
     * @return this placeholder including the additional value
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or contains a dot
     */
    public BundledPlaceholder append(String name, Object value) {
        put(this.values, name, value);
        return this;
    }

    /**
     * Returns the named values contained in this bundle.
     *
     * @return an unmodifiable view of the bundled values
     */
    public Map<String, Object> entries() {
        return Collections.unmodifiableMap(this.values);
    }

    private static void put(LinkedHashMap<String, Object> values, String name, Object value) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Bundled placeholder segment name must not be blank.");
        }
        if (name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Bundled placeholder segment name must not contain dots.");
        }
        values.put(name, value);
    }
}
