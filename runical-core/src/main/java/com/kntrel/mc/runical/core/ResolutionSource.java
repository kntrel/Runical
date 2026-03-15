package com.kntrel.mc.runical.core;

/**
 * Describes which lookup branch resolved a translation.
 */
public enum ResolutionSource {

    /**
     * The requested locale directly contained the translation key.
     */
    EXACT,

    /**
     * The general language locale resolved the translation, such as {@code es} for {@code es-mx}.
     */
    GENERAL,

    /**
     * A sibling regional locale for the same language resolved the translation.
     */
    SIBLING,

    /**
     * The configured default locale resolved the translation.
     */
    DEFAULT,

    /**
     * No locale resolved the translation key.
     */
    UNRESOLVED
}
