package com.rmjtromp.reflection;

import com.rmjtromp.ReflectionKit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

public final class ReflectedField {

    private final Class<?> targetClass;
    private final Object boundInstance;
    private final List<String> names;
    private Class<?> fieldTypeFilter;
    private Field resolvedField;

    ReflectedField(Class<?> targetClass, Object boundInstance, String... names) {
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
        this.boundInstance = boundInstance;
        this.names = Arrays.asList(names);
    }

    ReflectedField(Field resolvedField, Object boundInstance) {
        this.targetClass = resolvedField.getDeclaringClass();
        this.boundInstance = boundInstance;
        this.names = Collections.singletonList(resolvedField.getName());
        this.resolvedField = ReflectedClass.makeAccessible(resolvedField);
    }

    public ReflectedField type(Class<?> fieldType) {
        this.fieldTypeFilter = Objects.requireNonNull(fieldType, "fieldType");
        this.resolvedField = null;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get() {
        Field field = resolveField();
        try {
            return (T) field.get(boundInstance);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new ReflectionException("Failed to read field: " + field, exception);
        }
    }

    public <T> T get(Class<T> type) {
        return type.cast(get());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptional() {
        try {
            return Optional.ofNullable((T) get());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public ReflectedField set(Object value) {
        Field field = resolveField();
        try {
            field.set(boundInstance, value);
            return this;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new ReflectionException("Failed to set field: " + field, exception);
        }
    }

    public Field getField() {
        return resolveField();
    }

    public ReflectedObject query() {
        Object value = get();
        if (value == null) {
            throw new ReflectionException("Field value is null: " + resolveField());
        }
        return ReflectionKit.query(value);
    }

    public <A extends Annotation> A annotation(Class<A> type) {
        A annotation = resolveField().getAnnotation(type);
        if (annotation == null) {
            throw new ReflectionException("Annotation not found: " + type.getName() + " on " + resolveField());
        }
        return annotation;
    }

    public boolean hasAnnotation(Class<? extends Annotation> type) {
        return resolveField().isAnnotationPresent(type);
    }

    private Field resolveField() {
        if (resolvedField != null) {
            return resolvedField;
        }

        for (String name : names) {
            for (Class<?> clazz : ReflectedClass.buildSearchOrder(targetClass)) {
                Field candidate;
                try {
                    candidate = clazz.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    continue;
                }

                if (fieldTypeFilter != null && !ReflectedClass.matchesType(candidate.getType(), fieldTypeFilter)) {
                    continue;
                }

                resolvedField = ReflectedClass.makeAccessible(candidate);
                return resolvedField;
            }
        }

        String description = "Unable to resolve field " + names + " on " + targetClass.getName();
        if (fieldTypeFilter != null) {
            description += " with type " + fieldTypeFilter.getName();
        }
        throw new ReflectionException(description);
    }
}
