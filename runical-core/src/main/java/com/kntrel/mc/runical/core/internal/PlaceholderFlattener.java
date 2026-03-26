package com.kntrel.mc.runical.core.internal;

import com.kntrel.mc.runical.core.BundledPlaceholder;
import com.kntrel.mc.runical.core.Placeholder;

import java.util.HashMap;
import java.util.Map;

public final class PlaceholderFlattener {

    private PlaceholderFlattener() {
    }

    public static Map<String, String> flatten(Placeholder... args) {
        Map<String, String> placeholders = new HashMap<>();
        for (Placeholder argument : args) {
            if (argument == null) {
                continue;
            }
            collect(placeholders, argument.name(), argument.value());
        }
        return placeholders;
    }

    private static void collect(Map<String, String> placeholders, String name, Object value) {
        if (value instanceof BundledPlaceholder bundledPlaceholder) {
            placeholders.put(name, stringify(bundledPlaceholder.defaultValue()));
            for (Map.Entry<String, Object> entry : bundledPlaceholder.entries().entrySet()) {
                collect(placeholders, name + "." + entry.getKey(), entry.getValue());
            }
            return;
        }
        placeholders.put(name, stringify(value));
    }

    private static String stringify(Object value) {
        if (value instanceof BundledPlaceholder bundledPlaceholder) {
            return stringify(bundledPlaceholder.defaultValue());
        }
        return String.valueOf(value);
    }
}
