package com.kntrel.mc.runical.core.placeholder.internal;

import com.kntrel.mc.runical.core.placeholder.BundledPlaceholder;
import com.kntrel.mc.runical.core.placeholder.Translatable;
import com.kntrel.mc.runical.core.placeholder.TranslationProperty;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TranslatableBundleFactory {

    private static final ClassValue<Optional<TranslationDescriptor>> DESCRIPTORS = new ClassValue<>() {
        @Override
        protected Optional<TranslationDescriptor> computeValue(Class<?> type) {
            return TranslationDescriptor.describe(type);
        }
    };

    private TranslatableBundleFactory() {
    }

    public static BundledPlaceholder toBundledPlaceholder(Object value) {
        if (value == null) {
            return null;
        }
        return DESCRIPTORS.get(value.getClass())
                .map(descriptor -> descriptor.extract(value))
                .orElse(null);
    }

    private record TranslationDescriptor(List<PropertyAccessor> accessors) {

        private static Optional<TranslationDescriptor> describe(Class<?> type) {
            if (!type.isAnnotationPresent(Translatable.class)) {
                return Optional.empty();
            }

            List<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                hierarchy.add(current);
            }
            hierarchy.reversed();

            List<PropertyAccessor> accessors = new ArrayList<>();
            PropertyAccessor rootAccessor = null;

            for (Class<?> current : hierarchy) {
                Set<String> annotatedRecordComponentNames = new HashSet<>();
                Set<String> annotatedRecordAccessorNames = new HashSet<>();

                if (current.isRecord()) {
                    for (RecordComponent component : current.getRecordComponents()) {
                        TranslationProperty property = component.getAnnotation(TranslationProperty.class);
                        if (property == null) {
                            continue;
                        }

                        Method accessor = component.getAccessor();
                        accessor.trySetAccessible();

                        PropertyAccessor componentAccessor = new MethodPropertyAccessor(
                                resolvePropertyName(component.getName(), property.value()),
                                property.root(),
                                accessor
                        );
                        rootAccessor = ensureSingleRoot(type, rootAccessor, componentAccessor);
                        accessors.add(componentAccessor);
                        annotatedRecordComponentNames.add(component.getName());
                        annotatedRecordAccessorNames.add(accessor.getName());
                    }
                }

                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || annotatedRecordComponentNames.contains(field.getName())) {
                        continue;
                    }

                    TranslationProperty property = field.getAnnotation(TranslationProperty.class);
                    if (property == null) {
                        continue;
                    }

                    field.trySetAccessible();
                    PropertyAccessor fieldAccessor = new FieldPropertyAccessor(
                            resolvePropertyName(field.getName(), property.value()),
                            property.root(),
                            field
                    );
                    rootAccessor = ensureSingleRoot(type, rootAccessor, fieldAccessor);
                    accessors.add(fieldAccessor);
                }

                for (Method method : current.getDeclaredMethods()) {
                    if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (annotatedRecordAccessorNames.contains(method.getName()) && method.getParameterCount() == 0) {
                        continue;
                    }

                    TranslationProperty property = method.getAnnotation(TranslationProperty.class);
                    if (property == null) {
                        continue;
                    }

                    if (method.getParameterCount() != 0) {
                        throw new IllegalArgumentException(
                                "Annotated translation property method '%s#%s' must not declare parameters."
                                        .formatted(current.getName(), method.getName())
                        );
                    }
                    if (method.getReturnType() == Void.TYPE) {
                        throw new IllegalArgumentException(
                                "Annotated translation property method '%s#%s' must return a value."
                                        .formatted(current.getName(), method.getName())
                        );
                    }

                    method.trySetAccessible();
                    PropertyAccessor methodAccessor = new MethodPropertyAccessor(
                            resolvePropertyName(derivedMethodName(method), property.value()),
                            property.root(),
                            method
                    );
                    rootAccessor = ensureSingleRoot(type, rootAccessor, methodAccessor);
                    accessors.add(methodAccessor);
                }
            }

            return Optional.of(new TranslationDescriptor(List.copyOf(accessors)));
        }

        private BundledPlaceholder extract(Object instance) {
            BundledPlaceholder bundledPlaceholder = BundledPlaceholder.empty();
            for (PropertyAccessor accessor : this.accessors) {
                Object value = accessor.read(instance);
                bundledPlaceholder.append(accessor.name(), value);
                if (accessor.root()) {
                    bundledPlaceholder.appendDefault(value);
                }
            }
            return bundledPlaceholder;
        }

        private static PropertyAccessor ensureSingleRoot(Class<?> type, PropertyAccessor currentRoot, PropertyAccessor candidate) {
            if (!candidate.root()) {
                return currentRoot;
            }
            if (currentRoot != null) {
                throw new IllegalArgumentException(
                        "Translatable type '%s' declares more than one @TranslationProperty(root = true)."
                                .formatted(type.getName())
                );
            }
            return candidate;
        }

        private static String resolvePropertyName(String fallbackName, String explicitName) {
            String propertyName = explicitName.isBlank() ? fallbackName : explicitName;
            if (propertyName.isBlank()) {
                throw new IllegalArgumentException("Translation property names must not be blank.");
            }
            if (propertyName.indexOf('.') >= 0) {
                throw new IllegalArgumentException("Translation property names must not contain dots.");
            }
            return propertyName;
        }

        private static String derivedMethodName(Method method) {
            String methodName = method.getName();
            if (methodName.startsWith("get") && methodName.length() > 3) {
                return Introspector.decapitalize(methodName.substring(3));
            }
            return methodName;
        }
    }

    private sealed interface PropertyAccessor permits FieldPropertyAccessor, MethodPropertyAccessor {
        String name();

        boolean root();

        Object read(Object instance);
    }

    private record FieldPropertyAccessor(String name, boolean root, Field field) implements PropertyAccessor {

        @Override
        public Object read(Object instance) {
            try {
                return this.field.get(instance);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(
                        "Unable to read translation property field '%s#%s'."
                                .formatted(this.field.getDeclaringClass().getName(), this.field.getName()),
                        exception
                );
            }
        }
    }

    private record MethodPropertyAccessor(String name, boolean root, Method method) implements PropertyAccessor {

        @Override
        public Object read(Object instance) {
            try {
                return this.method.invoke(instance);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(
                        "Unable to invoke translation property method '%s#%s'."
                                .formatted(this.method.getDeclaringClass().getName(), this.method.getName()),
                        exception
                );
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException(
                        "Translation property method '%s#%s' threw an exception."
                                .formatted(this.method.getDeclaringClass().getName(), this.method.getName()),
                        exception.getCause()
                );
            }
        }
    }
}
