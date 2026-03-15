package com.kntrel.mc.runical.core.internal;

import com.kntrel.mc.runical.core.ListStyle;
import org.slf4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class YamlLocaleLoader {

    private static final String METADATA_ROOT = "_runical";
    private static final String LIST_FORMATS_KEY = "list_formats";

    private YamlLocaleLoader() {
    }

    public static LocaleBundle load(String locale, Path path, long version, Logger logger) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object root;
        try (InputStream inputStream = Files.newInputStream(path)) {
            root = yaml.load(inputStream);
        }

        if (root == null) {
            return new LocaleBundle(locale, Map.of(), Map.of(), version);
        }
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalStateException("Language file root must be a YAML map.");
        }

        Map<String, String> messages = new java.util.HashMap<>();
        Map<ListStyle, ListFormat> listFormats = new EnumMap<>(ListStyle.class);

        for (Map.Entry<?, ?> entry : rootMap.entrySet()) {
            String key = stringifyKey(entry.getKey());
            Object value = entry.getValue();
            if (METADATA_ROOT.equals(key)) {
                parseMetadata(value, listFormats, path, logger);
                continue;
            }
            flattenMessages(key, value, messages, path, logger);
        }

        return new LocaleBundle(locale, messages, listFormats, version);
    }

    private static void flattenMessages(String pathKey, Object value, Map<String, String> messages, Path path, Logger logger) {
        if (value instanceof Map<?, ?> childMap) {
            for (Map.Entry<?, ?> entry : childMap.entrySet()) {
                String childKey = stringifyKey(entry.getKey());
                flattenMessages(pathKey + "." + childKey, entry.getValue(), messages, path, logger);
            }
            return;
        }

        if (value instanceof java.util.List<?>) {
            logger.warn("Skipping list value for key '{}' in '{}'; list translations are not supported.", pathKey, path);
            return;
        }

        if (value == null) {
            logger.warn("Skipping null value for key '{}' in '{}'.", pathKey, path);
            return;
        }

        messages.put(pathKey, String.valueOf(value));
    }

    private static void parseMetadata(Object metadataNode, Map<ListStyle, ListFormat> listFormats, Path path, Logger logger) {
        if (!(metadataNode instanceof Map<?, ?> metadata)) {
            logger.warn("Ignoring '{}' metadata in '{}' because it is not a map.", METADATA_ROOT, path);
            return;
        }

        Object listFormatsNode = metadata.get(LIST_FORMATS_KEY);
        if (!(listFormatsNode instanceof Map<?, ?> styles)) {
            return;
        }

        for (ListStyle style : ListStyle.values()) {
            Object styleNode = styles.get(style.configKey());
            if (!(styleNode instanceof Map<?, ?> patternMap)) {
                continue;
            }

            ListFormat fallback = ListFormat.defaultFor(style);
            listFormats.put(style, new ListFormat(
                    scalarOrFallback(patternMap.get("empty"), fallback.empty()),
                    scalarOrFallback(patternMap.get("two"), fallback.two()),
                    scalarOrFallback(patternMap.get("start"), fallback.start()),
                    scalarOrFallback(patternMap.get("middle"), fallback.middle()),
                    scalarOrFallback(patternMap.get("end"), fallback.end())
            ));
        }
    }

    private static String scalar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String scalarOrFallback(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String stringifyKey(Object key) {
        if (key == null) {
            throw new IllegalStateException("YAML keys must not be null.");
        }
        return String.valueOf(key);
    }
}
