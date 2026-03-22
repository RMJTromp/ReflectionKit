package com.rmjtromp;

import com.rmjtromp.reflection.ReflectedClass;
import com.rmjtromp.reflection.ReflectedObject;
import com.rmjtromp.reflection.ReflectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectionKitTest {

    @Test
    void queryClassRejectsNull() {
        ReflectionException exception = assertThrows(ReflectionException.class, () -> ReflectionKit.query((Class<?>) null));
        assertEquals("Target class cannot be null", exception.getMessage());
    }

    @Test
    void queryInstanceRejectsNull() {
        ReflectionException exception = assertThrows(ReflectionException.class, () -> ReflectionKit.query((Object) null));
        assertEquals("Target instance cannot be null", exception.getMessage());
    }

    @Test
    void queryClassReturnsReflectedClass() {
        ReflectedClass reflectedClass = ReflectionKit.query(Sample.class);
        assertEquals(Sample.class, reflectedClass.getTargetClass());
    }

    @Test
    void queryInstanceReturnsReflectedObject() {
        Sample sample = new Sample();
        ReflectedObject reflectedObject = ReflectionKit.query(sample);
        assertEquals(Sample.class, reflectedObject.getTargetClass());
        assertInstanceOf(Sample.class, reflectedObject.getInstance());
    }

    private static class Sample {
    }
}
