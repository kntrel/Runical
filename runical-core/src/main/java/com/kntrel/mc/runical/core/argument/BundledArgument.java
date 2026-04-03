package com.kntrel.mc.runical.core.argument;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Group of placeholder values that can be mounted under a single placeholder namespace.
 *
 * <p>Bundled placeholders are flattened into dotted placeholder tokens during rendering. For
 * example, calling {@code translate(...).argument("person", bundled)} exposes entries such as
 * {@code {person.name}} and {@code {person.age}}.
 */
public final class BundledArgument {

    private final LinkedHashMap<String, Object> values;
    private boolean hasDefaultValue;
    private Object defaultValue;

    private BundledArgument(LinkedHashMap<String, Object> values, boolean hasDefaultValue, Object defaultValue) {
        this.values = values;
        this.hasDefaultValue = hasDefaultValue;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates an empty bundled placeholder without a namespace default value.
     *
     * @return an empty bundled placeholder
     */
    public static BundledArgument empty() {
        return new BundledArgument(new LinkedHashMap<>(), false, null);
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
    public static BundledArgument of(String name, Object value) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        put(values, name, value);
        return new BundledArgument(values, true, value);
    }

    /**
     * Adds a named value to the bundled placeholder.
     *
     * <p>If the bundled placeholder already contains the same segment name, the new value replaces
     * the previous value. The bundle's default value is unchanged.
     *
     * @param name bundled placeholder segment name
     * @param value bundled placeholder value, which may be {@code null}
     * @return this placeholder including the additional value
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or contains a dot
     */
    public BundledArgument append(String name, Object value) {
        put(this.values, name, value);
        return this;
    }

    /**
     * Replaces the value used when the bundle's namespace itself is referenced.
     *
     * <p>For example, if a bundle is passed through {@code argument("person", bundled)}, then this
     * method controls the value rendered for {@code {person}} while dotted entries such as
     * {@code {person.name}} continue to come from the bundled entries.
     *
     * @param value namespace default value, which may be {@code null}
     * @return this bundled placeholder
     */
    public BundledArgument appendDefault(Object value) {
        this.hasDefaultValue = true;
        this.defaultValue = value;
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

    /**
     * Returns whether this bundle exposes a namespace default value.
     *
     * @return {@code true} when the namespace token itself should resolve
     */
    public boolean hasDefaultValue() {
        return this.hasDefaultValue;
    }

    /**
     * Returns the value rendered when the bundle namespace itself is referenced.
     *
     * @return the namespace default value, which may be {@code null}
     */
    public Object defaultValue() {
        return this.defaultValue;
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
