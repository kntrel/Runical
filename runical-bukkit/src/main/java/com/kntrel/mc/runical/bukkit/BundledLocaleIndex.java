package com.kntrel.mc.runical.bukkit;

import com.kntrel.mc.runical.core.internal.LocaleSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record BundledLocaleIndex(Map<String, String> localeResources, Map<String, List<String>> localesByLanguage) {
    public static BundledLocaleIndex empty() {
        return new BundledLocaleIndex(Map.of(), Map.of());
    }

    public Optional<String> resourcePath(String locale) {
        return Optional.ofNullable(this.localeResources.get(LocaleSupport.normalizeLocale(locale)));
    }

    public List<String> localesForLanguage(String language) {
        return this.localesByLanguage.getOrDefault(LocaleSupport.normalizeLocale(language), List.of());
    }
}
