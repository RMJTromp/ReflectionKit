package com.rmjtromp.reflection.mapping;

import com.rmjtromp.reflection.ReflectionException;

import java.lang.reflect.Modifier;
import java.util.Objects;

public final class MappedProxy {

    private final Object target;

    public MappedProxy(Object target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    @SuppressWarnings("unchecked")
    public <T> T to(Class<T> type) {
        Objects.requireNonNull(type, "type");

        if (!type.isAnnotationPresent(Proxy.class)) {
            throw new ReflectionException("Target type must be annotated with @Proxy: " + type.getName());
        }

        if (type.isInterface()) {
            throw new ReflectionException("Interfaces are not supported, use an abstract class: " + type.getName());
        }

        if (!Modifier.isAbstract(type.getModifiers())) {
            throw new ReflectionException("Target type must be abstract: " + type.getName());
        }

        if (Modifier.isFinal(type.getModifiers())) {
            throw new ReflectionException("Target type must not be final: " + type.getName());
        }

        return ProxyFactory.create(target, type);
    }

}
