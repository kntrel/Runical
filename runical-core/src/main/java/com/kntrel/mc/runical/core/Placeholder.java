package com.kntrel.mc.runical.core;

import java.util.Objects;

public record Placeholder(String name, Object value) {

    public Placeholder {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Placeholder name must not be blank.");
        }
    }

    public static Placeholder of(String name, Object value) {
        return new Placeholder(name, value);
    }
}
