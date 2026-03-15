package com.kntrel.mc.runical.core.internal;

import java.util.Locale;
import java.util.Objects;

public final class LocaleSupport {

    private LocaleSupport() {
    }

    public static String normalizeLocale(String locale) {
        Objects.requireNonNull(locale, "locale");
        String normalized = locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Locale must not be blank.");
        }
        return normalized;
    }

    public static String languageOf(String locale) {
        String normalized = normalizeLocale(locale);
        int separator = normalized.indexOf('-');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    public static String generalLocale(String locale) {
        String normalized = normalizeLocale(locale);
        int separator = normalized.indexOf('-');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    public static boolean isRegional(String locale) {
        return normalizeLocale(locale).contains("-");
    }
}
