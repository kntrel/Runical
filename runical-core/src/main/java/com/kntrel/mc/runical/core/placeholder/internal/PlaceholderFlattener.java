package com.kntrel.mc.runical.core.placeholder.internal;

import com.kntrel.mc.runical.core.placeholder.BundledPlaceholder;
import com.kntrel.mc.runical.core.placeholder.Placeholder;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class PlaceholderFlattener {

    private PlaceholderFlattener() {
    }

    public static Map<String, String> flatten(Placeholder... args) {
        Map<String, String> placeholders = new HashMap<>();
        IdentityHashMap<Object, Boolean> activeValues = new IdentityHashMap<>();
        for (Placeholder argument : args) {
            if (argument == null) {
                continue;
            }
            collect(placeholders, argument.name(), argument.value(), activeValues);
        }
        return placeholders;
    }

    private static void collect(
            Map<String, String> placeholders,
            String name,
            Object value,
            IdentityHashMap<Object, Boolean> activeValues
    ) {
        BundledValue bundledValue = bundledValue(value);
        if (bundledValue != null) {
            if (activeValues.put(bundledValue.cycleKey(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException(
                        "Detected a cyclic bundled or translatable value while resolving placeholder '" + name + "'."
                );
            }
            try {
                String defaultValue = stringifyBundleDefault(bundledValue.bundle(), name, activeValues);
                if (defaultValue != null) {
                    placeholders.put(name, defaultValue);
                }
                for (Map.Entry<String, Object> entry : bundledValue.bundle().entries().entrySet()) {
                    collect(placeholders, name + "." + entry.getKey(), entry.getValue(), activeValues);
                }
            } finally {
                activeValues.remove(bundledValue.cycleKey());
            }
            return;
        }
        placeholders.put(name, String.valueOf(value));
    }

    private static String stringifyBundleDefault(
            BundledPlaceholder bundledPlaceholder,
            String name,
            IdentityHashMap<Object, Boolean> activeValues
    ) {
        if (!bundledPlaceholder.hasDefaultValue()) {
            return null;
        }
        return stringify(bundledPlaceholder.defaultValue(), name, activeValues);
    }

    private static String stringify(Object value, String name, IdentityHashMap<Object, Boolean> activeValues) {
        BundledValue bundledValue = bundledValue(value);
        if (bundledValue == null) {
            return String.valueOf(value);
        }
        if (activeValues.put(bundledValue.cycleKey(), Boolean.TRUE) != null) {
            throw new IllegalArgumentException(
                    "Detected a cyclic bundled or translatable value while resolving placeholder '" + name + "'."
            );
        }
        try {
            return stringifyBundleDefault(bundledValue.bundle(), name, activeValues);
        } finally {
            activeValues.remove(bundledValue.cycleKey());
        }
    }

    private static BundledValue bundledValue(Object value) {
        if (value instanceof BundledPlaceholder bundledPlaceholder) {
            return new BundledValue(bundledPlaceholder, bundledPlaceholder);
        }

        BundledPlaceholder translatableBundle = TranslatableBundleFactory.toBundledPlaceholder(value);
        if (translatableBundle != null) {
            return new BundledValue(translatableBundle, value);
        }

        return null;
    }

    private record BundledValue(BundledPlaceholder bundle, Object cycleKey) {
    }
}
