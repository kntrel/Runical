package com.kntrel.mc.runical.core;

import java.util.*;

final class MountedAliases {
    private final Map<String, String> aliases_;
    private final List<MountedAlias> orderedAliases_;

    private MountedAliases(Map<String, String> aliases, List<MountedAlias> orderedAliases) {
        this.aliases_ = aliases;
        this.orderedAliases_ = orderedAliases;
    }

    static MountedAliases empty() {
        return new MountedAliases(Map.of(), List.of());
    }

    MountedAliases withMount(String canonicalPath, String aliasPath) {
        String normalizedCanonicalPath = this.rewrite(canonicalPath);
        if (normalizedCanonicalPath.equals(aliasPath)) {
            throw new IllegalArgumentException("Mounted alias path must not resolve to itself.");
        }

        String existingTarget = this.aliases_.get(aliasPath);
        if (existingTarget != null) {
            if (existingTarget.equals(normalizedCanonicalPath)) {
                return this;
            }
            throw new IllegalArgumentException("Mounted alias path '%s' is already registered.".formatted(aliasPath));
        }

        for (String existingAlias : this.aliases_.keySet()) {
            if (BaseRunical.overlaps(existingAlias, aliasPath)) {
                throw new IllegalArgumentException(
                        "Mounted alias path '%s' overlaps existing mount '%s'.".formatted(aliasPath, existingAlias)
                );
            }
        }

        LinkedHashMap<String, String> updatedAliases = new LinkedHashMap<>(this.aliases_);
        updatedAliases.put(aliasPath, normalizedCanonicalPath);
        return from(updatedAliases);
    }

    String rewrite(String path) {
        String current = path;
        Set<String> visited = new HashSet<>();

        while (true) {
            if (!visited.add(current)) {
                throw new IllegalStateException("Mounted alias cycle detected while resolving path '%s'.".formatted(path));
            }

            MountedAlias match = this.match(current);
            if (match == null) {
                return current;
            }
            current = match.rewrite(current);
        }
    }

    private MountedAlias match(String path) {
        for (MountedAlias alias : this.orderedAliases_) {
            if (alias.matches(path)) {
                return alias;
            }
        }
        return null;
    }

    private static MountedAliases from(Map<String, String> aliases) {
        if (aliases.isEmpty()) {
            return empty();
        }

        List<MountedAlias> orderedAliases = aliases.entrySet().stream()
                .map(entry -> new MountedAlias(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((MountedAlias alias) -> alias.aliasPath().length()).reversed())
                .toList();

        return new MountedAliases(Map.copyOf(aliases), orderedAliases);
    }
}
