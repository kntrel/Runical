package com.kntrel.mc.runical.core;

public record ResolvedTranslation(
        String requestedLocale,
        String key,
        String resolvedLocale,
        String value,
        ResolutionSource source
) {

    public boolean found() {
        return this.value != null;
    }

    public String orKey() {
        return this.value != null ? this.value : this.key;
    }
}
