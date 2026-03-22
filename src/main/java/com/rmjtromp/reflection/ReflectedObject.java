package com.rmjtromp.reflection;

import lombok.Getter;

import java.util.Objects;

public final class ReflectedObject extends ReflectedClass {

    @Getter
    private final Object instance;

    public ReflectedObject(Object instance) {
        super(Objects.requireNonNull(instance, "instance").getClass(), instance);
        this.instance = instance;
    }

}
