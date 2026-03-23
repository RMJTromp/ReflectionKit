package com.rmjtromp.reflection.mapping;

import com.rmjtromp.reflection.ReflectedObject;

public abstract class MappedBase {

    ReflectedObject __reflected;

    @SuppressWarnings("unchecked")
    protected <T> T call(String methodName, Object... args) {
        return __reflected.method(methodName).call(args);
    }

    protected <T> T field(String fieldName) {
        return __reflected.field(fieldName).get();
    }

    protected <T> T field(String fieldName, Class<T> type) {
        return __reflected.field(fieldName).get(type);
    }

    protected Object getTarget() {
        return __reflected.getInstance();
    }

}
