package com.kntrel.mc.runical.core;

/**
 * Result of a translation lookup.
 *
 * @param requestedLocale normalized locale requested by the caller
 * @param key translation key that was requested
 * @param resolvedLocale normalized locale that supplied the translation, or {@code null} when the
 *                       key could not be resolved
 * @param value rendered translation value, or {@code null} when unresolved
 * @param source lookup branch that produced the result
 */
public record ResolvedTranslation(
        String requestedLocale,
        String key,
        String resolvedLocale,
        String value,
        ResolutionSource source
) {

    /**
     * Returns whether the lookup found a translation value.
     *
     * @return {@code true} when {@link #value()} is not {@code null}
     */
    public boolean found() {
        return this.value != null;
    }

    /**
     * Returns the resolved value when present, otherwise the original translation key.
     *
     * @return the resolved value or {@link #key()}
     */
    public String orKey() {
        return this.value != null ? this.value : this.key;
    }
}
