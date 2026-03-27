package com.kntrel.mc.runical.core.placeholder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field, zero-argument method, or record component as a translation-time property.
 *
 * <p>When {@link #value()} is blank, the property name is derived from the annotated member. Method
 * names keep their original name unless they follow a getter pattern such as {@code getName()},
 * which contributes {@code name}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface TranslationProperty {

    /**
     * Explicit property name. When blank, the annotated member name is used.
     *
     * @return the exposed property name
     */
    String value() default "";

    /**
     * Whether this property also provides the namespace root value.
     *
     * @return {@code true} when this property should resolve the namespace token itself
     */
    boolean root() default false;
}
