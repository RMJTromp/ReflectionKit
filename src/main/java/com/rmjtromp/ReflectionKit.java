package com.rmjtromp;

import com.rmjtromp.reflection.ReflectedClass;
import com.rmjtromp.reflection.ReflectedObject;
import com.rmjtromp.reflection.ReflectionException;
import com.rmjtromp.reflection.mapping.MappedProxy;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ReflectionKit {

    public static ReflectedClass query(Class<?> clazz) {
        if (clazz == null) {
            throw new ReflectionException("Target class cannot be null");
        }
        return new ReflectedClass(clazz);
    }

    public static ReflectedObject query(Object instance) {
        if (instance == null) {
            throw new ReflectionException("Target instance cannot be null");
        }
        return new ReflectedObject(instance);
    }

    public static MappedProxy map(Object instance) {
        if (instance == null) {
            throw new ReflectionException("Target instance cannot be null");
        }
        return new MappedProxy(instance);
    }

}
