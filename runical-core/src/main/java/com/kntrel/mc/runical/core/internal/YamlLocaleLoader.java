package com.kntrel.mc.runical.core.internal;

import com.kntrel.mc.runical.core.ListStyle;
import org.slf4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class YamlLocaleLoader {

    private static final String METADATA_ROOT = "_runical";
    private static final String LIST_FORMATS_KEY = "list_formats";
    private static final Tag FILE_TAG = new Tag("!file");

    private YamlLocaleLoader() {}

    public static LocaleBundle load(
            String locale,
            Path path,
            long version,
            Logger logger,
            MissingTaggedFileResolver missingTaggedFileResolver
    ) throws IOException {
        Yaml yaml = new Yaml(new FileTagConstructor(path, new LoaderOptions()));
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
                parseMetadata(value, listFormats, path, logger, missingTaggedFileResolver);
                continue;
            }
            flattenMessages(key, value, messages, path, logger, missingTaggedFileResolver);
        }

        return new LocaleBundle(locale, messages, listFormats, version);
    }

    private static void flattenMessages(
            String pathKey,
            Object value,
            Map<String, String> messages,
            Path path,
            Logger logger,
            MissingTaggedFileResolver missingTaggedFileResolver
    ) {
        if (value instanceof Map<?, ?> childMap) {
            for (Map.Entry<?, ?> entry : childMap.entrySet()) {
                String childKey = stringifyKey(entry.getKey());
                flattenMessages(pathKey + "." + childKey, entry.getValue(), messages, path, logger, missingTaggedFileResolver);
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

        String scalar = resolveScalar(value, pathKey, path, logger, missingTaggedFileResolver);
        if (scalar == null) {
            return;
        }
        messages.put(pathKey, scalar);
    }

    private static void parseMetadata(
            Object metadataNode,
            Map<ListStyle, ListFormat> listFormats,
            Path path,
            Logger logger,
            MissingTaggedFileResolver missingTaggedFileResolver
    ) {
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
                    scalarOrFallback(patternMap.get("empty"), fallback.empty(), path, metadataPath(style, "empty"), logger, missingTaggedFileResolver),
                    scalarOrFallback(patternMap.get("two"), fallback.two(), path, metadataPath(style, "two"), logger, missingTaggedFileResolver),
                    scalarOrFallback(patternMap.get("start"), fallback.start(), path, metadataPath(style, "start"), logger, missingTaggedFileResolver),
                    scalarOrFallback(patternMap.get("middle"), fallback.middle(), path, metadataPath(style, "middle"), logger, missingTaggedFileResolver),
                    scalarOrFallback(patternMap.get("end"), fallback.end(), path, metadataPath(style, "end"), logger, missingTaggedFileResolver)
            ));
        }
    }

    private static String resolveScalar(
            Object value,
            String key,
            Path localePath,
            Logger logger,
            MissingTaggedFileResolver missingTaggedFileResolver
    ) {
        if (value == null) {
            return null;
        }
        if (value instanceof FileReference fileReference) {
            return readFileReference(fileReference, key, localePath, logger, missingTaggedFileResolver);
        }
        return String.valueOf(value);
    }

    private static String scalarOrFallback(
            Object value,
            String fallback,
            Path localePath,
            String key,
            Logger logger,
            MissingTaggedFileResolver missingTaggedFileResolver
    ) {
        String scalar = resolveScalar(value, key, localePath, logger, missingTaggedFileResolver);
        return scalar == null ? fallback : scalar;
    }

    private static String stringifyKey(Object key) {
        if (key == null) {
            throw new IllegalStateException("YAML keys must not be null.");
        }
        return String.valueOf(key);
    }

    private static String readFileReference(
            FileReference fileReference,
            String key,
            Path localePath,
            Logger logger,
            MissingTaggedFileResolver missingTaggedFileResolver
    ) {
        Path referencedPath;
        try {
            referencedPath = Path.of(fileReference.path());
        } catch (InvalidPathException exception) {
            logger.warn(
                    "Skipping !file value for key '{}' in '{}'; '{}' is not a valid path.",
                    key,
                    localePath,
                    fileReference.path()
            );
            return null;
        }

        Path resolvedPath = referencedPath.isAbsolute()
                ? referencedPath.normalize()
                : localePath.toAbsolutePath().normalize().getParent().resolve(referencedPath).normalize();
        if (!Files.isRegularFile(resolvedPath)) {
            resolvedPath = missingTaggedFileResolver.resolve(localePath, resolvedPath)
                    .map(path -> path.toAbsolutePath().normalize())
                    .orElse(resolvedPath);
        }
        if (!Files.isRegularFile(resolvedPath)) {
            logger.warn(
                    "Skipping !file value for key '{}' in '{}'; referenced file '{}' does not exist or is not a regular file.",
                    key,
                    localePath,
                    resolvedPath
            );
            return null;
        }

        try {
            return Files.readString(resolvedPath);
        } catch (IOException exception) {
            logger.warn(
                    "Skipping !file value for key '{}' in '{}'; unable to read '{}'.",
                    key,
                    localePath,
                    resolvedPath,
                    exception
            );
            return null;
        }
    }

    private static String metadataPath(ListStyle style, String patternKey) {
        return METADATA_ROOT + "." + LIST_FORMATS_KEY + "." + style.configKey() + "." + patternKey;
    }

    private record FileReference(String path) {
    }

    @FunctionalInterface
    public interface MissingTaggedFileResolver {
        Optional<Path> resolve(Path localePath, Path referencedPath);
    }

    private static final class FileTagConstructor extends SafeConstructor {

        private FileTagConstructor(Path localePath, LoaderOptions loaderOptions) {
            super(loaderOptions);
            this.yamlConstructors.put(FILE_TAG, new ConstructFileTag(localePath));
        }
    }

    private static final class ConstructFileTag extends AbstractConstruct {
        private final Path localePath;

        private ConstructFileTag(Path localePath) {
            this.localePath = localePath;
        }

        @Override public Object construct(Node node) {
            if (!(node instanceof ScalarNode scalarNode)) {
                throw new IllegalStateException("The !file tag in '" + this.localePath + "' must be used with a scalar value.");
            }
            return new FileReference(scalarNode.getValue());
        }
    }
}
