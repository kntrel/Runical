package com.kntrel.mc.runical.core.internal;

import com.kntrel.mc.runical.core.ListStyle;
import com.kntrel.mc.runical.core.argument.internal.PlaceholderRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record ListFormat(
        String empty,
        String two,
        String start,
        String middle,
        String end
) {

    public ListFormat {
        Objects.requireNonNull(empty, "empty");
        Objects.requireNonNull(two, "two");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(middle, "middle");
        Objects.requireNonNull(end, "end");
    }

    public static ListFormat defaultFor(ListStyle style) {
        return switch (style) {
            case AND -> new ListFormat("", "{0} and {1}", "{0}, {1}", "{0}, {1}", "{0}, and {1}");
            case OR -> new ListFormat("", "{0} or {1}", "{0}, {1}", "{0}, {1}", "{0}, or {1}");
        };
    }

    public ListFormat withFallback(ListFormat fallback) {
        return new ListFormat(
                pick(this.empty, fallback.empty),
                pick(this.two, fallback.two),
                pick(this.start, fallback.start),
                pick(this.middle, fallback.middle),
                pick(this.end, fallback.end)
        );
    }

    public String format(Collection<?> items) {
        List<String> parts = new ArrayList<>(items.size());
        for (Object item : items) {
            parts.add(String.valueOf(item));
        }

        int size = parts.size();
        if (size == 0) {
            return this.empty;
        }
        if (size == 1) {
            return parts.get(0);
        }
        if (size == 2) {
            return apply(parts.get(0), parts.get(1), this.two);
        }

        String formatted = apply(parts.get(0), parts.get(1), this.start);
        for (int index = 2; index < size - 1; index++) {
            formatted = apply(formatted, parts.get(index), this.middle);
        }
        return apply(formatted, parts.get(size - 1), this.end);
    }

    private static String apply(String left, String right, String template) {
        return PlaceholderRenderer.render(template, token -> switch (token) {
            case "0" -> left;
            case "1" -> right;
            default -> null;
        });
    }

    private static String pick(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
