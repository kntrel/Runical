package com.kntrel.mc.runical.core.placeholder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type whose annotated members can be projected into translation-time properties.
 *
 * <p>This annotation is not inherited. A subclass of a translatable type must declare
 * {@code @Translatable} itself to opt into reflective translation-property extraction.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Translatable {
}
