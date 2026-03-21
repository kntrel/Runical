package com.kntrel.mc.runical.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory utilities for composing multiple canonical translators into a single translator view.
 *
 * <p>Composition keeps one primary translator for unprefixed local keys and mounts additional
 * canonical translators at explicit canonical prefixes such as {@code hierarchy}. The result still
 * implements {@link BaseTranslator}, so downstream code continues depending on the existing
 * translator contract.
 */
public final class Translators {

    private Translators() {
    }

    /**
     * Starts composing a translator view around the given primary translator.
     *
     * @param primary primary translator that owns unprefixed local keys
     * @return a composition builder
     * @throws NullPointerException if {@code primary} is {@code null}
     */
    public static Builder compose(BaseTranslator primary) {
        return new Builder(primary);
    }

    static String requireSegment(String segment) {
        Objects.requireNonNull(segment, "segment");
        String normalizedSegment = segment.trim();
        if (normalizedSegment.isBlank()) {
            throw new IllegalArgumentException("Translator child segment must not be blank.");
        }
        if (normalizedSegment.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Translator child segment must not contain dots.");
        }
        return normalizedSegment;
    }

    static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Translation key must not be blank.");
        }
        return key;
    }

    private static String normalizeMountPath(String path) {
        Objects.requireNonNull(path, "path");
        String normalizedPath = path.trim();
        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException("Mounted translator path must not be blank.");
        }

        String[] rawSegments = normalizedPath.split("\\.", -1);
        String[] normalizedSegments = new String[rawSegments.length];
        for (int index = 0; index < rawSegments.length; index++) {
            normalizedSegments[index] = requireSegment(rawSegments[index]);
        }
        return String.join(".", normalizedSegments);
    }

    private static boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + ".") || right.startsWith(left + ".");
    }


    /**
     * Builder for a mounted translator composition.
     */
    public static final class Builder {

        //FIELDS
        private final BaseTranslator primary_;
        private final BaseRunical root_;
        private final LinkedHashMap<String, BaseTranslator> mounts_;


        //CONSTRUCTOR
        private Builder(BaseTranslator primary) {
            this.primary_ = Objects.requireNonNull(primary, "primary");
            this.root_ = primary.getRoot();
            this.mounts_ = new LinkedHashMap<>();
        }


        /**
         * Mounts a canonical translator at the given canonical prefix.
         *
         * <p>The mounted translator must share the same root resolver as the primary translator and
         * must expose a canonical non-root path equal to the supplied mount path. Overlapping mount
         * paths such as {@code hierarchy} and {@code hierarchy.roles} are rejected to keep child
         * scoping predictable; nest a secondary composition instead when that shape is needed. An
         * exact mounted prefix owns that subtree and therefore takes precedence over the primary
         * translator for keys beneath the same prefix.
         *
         * @param path canonical mount path exposed from the composed translator
         * @param translator translator to mount at that path
         * @return this builder
         * @throws NullPointerException if {@code path} or {@code translator} is {@code null}
         * @throws IllegalArgumentException if the mount path is invalid, overlaps another mount,
         *                                  differs from the translator's canonical path, or uses a
         *                                  different root resolver
         */
        public Builder mount(String path, BaseTranslator translator) {
            String normalizedPath = normalizeMountPath(path);
            BaseTranslator mountedTranslator = Objects.requireNonNull(translator, "translator");

            if (mountedTranslator.getRoot() != this.root_) {
                throw new IllegalArgumentException("Mounted translators must share the same root as the primary translator.");
            }

            String mountedPath = mountedTranslator.getPath();
            if (mountedPath == null || mountedPath.isBlank()) {
                throw new IllegalArgumentException("Mounted translators must expose a canonical non-root path.");
            }
            if (!normalizedPath.equals(mountedPath)) {
                throw new IllegalArgumentException("Mounted translator path '%s' does not match mount path '%s'.".formatted(mountedPath, normalizedPath));
            }

            for (String existingPath : this.mounts_.keySet()) {
                if (overlaps(existingPath, normalizedPath)) {
                    throw new IllegalArgumentException(
                            "Mounted translator path '%s' overlaps existing mount '%s'.".formatted(normalizedPath, existingPath)
                    );
                }
            }

            this.mounts_.put(normalizedPath, mountedTranslator);
            return this;
        }

        /**
         * Builds the composed translator.
         *
         * @return the primary translator when no mounts were registered, otherwise a composite
         *         translator that preserves the {@link BaseTranslator} contract
         */
        public BaseTranslator build() {
            return CompositeBaseTranslator.create(this.primary_, Map.copyOf(this.mounts_));
        }
    }
}
