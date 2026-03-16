package com.kntrel.mc.runical.core.internal;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class LocaleIndex {

    private final Map<String, Path> localeFiles;
    private final Map<String, List<String>> localesByLanguage;

    private LocaleIndex(Map<String, Path> localeFiles, Map<String, List<String>> localesByLanguage) {
        this.localeFiles = Collections.unmodifiableMap(localeFiles);
        this.localesByLanguage = Collections.unmodifiableMap(localesByLanguage);
    }

    public static LocaleIndex empty() {
        return new LocaleIndex(Map.of(), Map.of());
    }

    public static LocaleIndex scan(Path languagesDirectory, Logger logger) {
        try {
            Files.createDirectories(languagesDirectory);
        } catch (IOException exception) {
            logger.warn("Unable to create or access language directory '{}'.", languagesDirectory, exception);
            return LocaleIndex.empty();
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(languagesDirectory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(LocaleIndex::isYamlFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            logger.warn("Unable to scan language directory '{}'.", languagesDirectory, exception);
            return LocaleIndex.empty();
        }

        Map<String, Path> localeFiles = new HashMap<>();
        Map<String, List<String>> localesByLanguage = new HashMap<>();

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            int extensionSeparator = fileName.lastIndexOf('.');
            String rawLocale = extensionSeparator >= 0 ? fileName.substring(0, extensionSeparator) : fileName;
            String normalizedLocale;
            try {
                normalizedLocale = LocaleSupport.normalizeLocale(rawLocale);
            } catch (IllegalArgumentException exception) {
                logger.warn("Skipping language file '{}' because its locale name is invalid.", file);
                continue;
            }

            Path previous = localeFiles.putIfAbsent(normalizedLocale, file);
            if (previous != null) {
                logger.warn(
                        "Skipping duplicate locale file '{}' because '{}' already maps to locale '{}'.",
                        file,
                        previous,
                        normalizedLocale
                );
                continue;
            }

            localesByLanguage.computeIfAbsent(LocaleSupport.languageOf(normalizedLocale), ignored -> new ArrayList<>())
                    .add(normalizedLocale);
        }

        for (List<String> locales : localesByLanguage.values()) {
            Collections.sort(locales);
        }

        return new LocaleIndex(localeFiles, localesByLanguage);
    }

    public boolean hasLocale(String locale) {
        return this.localeFiles.containsKey(LocaleSupport.normalizeLocale(locale));
    }

    public Optional<Path> pathFor(String locale) {
        return Optional.ofNullable(this.localeFiles.get(LocaleSupport.normalizeLocale(locale)));
    }

    public List<String> localesForLanguage(String language) {
        return this.localesByLanguage.getOrDefault(LocaleSupport.normalizeLocale(language), List.of());
    }

    public LocaleIndex withLocale(String locale, Path path) {
        String normalizedLocale = LocaleSupport.normalizeLocale(locale);
        Path normalizedPath = path.toAbsolutePath().normalize();
        Path existing = this.localeFiles.get(normalizedLocale);
        if (existing != null) {
            return this;
        }

        Map<String, Path> localeFiles = new HashMap<>(this.localeFiles);
        localeFiles.put(normalizedLocale, normalizedPath);

        Map<String, List<String>> localesByLanguage = new HashMap<>(this.localesByLanguage);
        String language = LocaleSupport.languageOf(normalizedLocale);
        List<String> locales = new ArrayList<>(localesByLanguage.getOrDefault(language, List.of()));
        if (!locales.contains(normalizedLocale)) {
            locales.add(normalizedLocale);
            Collections.sort(locales);
        }
        localesByLanguage.put(language, locales);
        return new LocaleIndex(localeFiles, localesByLanguage);
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
