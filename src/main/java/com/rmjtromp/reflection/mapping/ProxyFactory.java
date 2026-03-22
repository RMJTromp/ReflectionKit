package com.rmjtromp.reflection.mapping;

import com.rmjtromp.ReflectionKit;
import com.rmjtromp.reflection.ReflectedObject;
import com.rmjtromp.reflection.ReflectionException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class ProxyFactory {

    @SuppressWarnings("unchecked")
    static <T> T create(Object target, Class<T> proxyType) {
        ReflectedObject reflected = ReflectionKit.query(target);

        try {
            // Generate subclass with ByteBuddy, intercepting abstract methods
            MethodInterceptor interceptor = new MethodInterceptor(reflected);

            DynamicType.Builder<? extends T> builder = new ByteBuddy()
                    .subclass(proxyType);

            // Intercept abstract methods
            builder = builder
                    .method(ElementMatchers.isAbstract())
                    .intercept(MethodDelegation.to(interceptor));

            Class<? extends T> proxyClass = builder
                    .make()
                    .load(proxyType.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();

            T proxy = proxyClass.getDeclaredConstructor().newInstance();

            // Inject MappedBase reference if applicable
            if (MappedBase.class.isAssignableFrom(proxyType)) {
                Field reflectedField = MappedBase.class.getDeclaredField("__reflected");
                reflectedField.setAccessible(true);
                reflectedField.set(proxy, reflected);
            }

            // Eagerly populate declared fields
            populateFields(proxy, proxyType, reflected);

            return proxy;
        } catch (ReflectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ReflectionException("Failed to create proxy for " + proxyType.getName(), e);
        }
    }

    private static <T> void populateFields(T proxy, Class<T> proxyType, ReflectedObject reflected) {
        // Collect fields from the proxy type hierarchy (up to but not including MappedBase/Object)
        List<Field> proxyFields = collectProxyFields(proxyType);

        for (Field proxyField : proxyFields) {
            if (Modifier.isStatic(proxyField.getModifiers())) {
                continue;
            }

            Object value = resolveFieldValue(proxyField, reflected);

            // Auto-wrap @Proxy typed fields
            if (value != null && proxyField.getType().isAnnotationPresent(Proxy.class)) {
                value = ReflectionKit.map(value).to(proxyField.getType());
            }

            try {
                proxyField.setAccessible(true);
                proxyField.set(proxy, value);
            } catch (IllegalAccessException e) {
                throw new ReflectionException("Failed to set proxy field: " + proxyField.getName(), e);
            }
        }
    }

    private static List<Field> collectProxyFields(Class<?> proxyType) {
        List<Field> fields = new ArrayList<>();
        Class<?> cursor = proxyType;
        while (cursor != null && cursor != Object.class && cursor != MappedBase.class) {
            for (Field field : cursor.getDeclaredFields()) {
                fields.add(field);
            }
            cursor = cursor.getSuperclass();
        }
        return fields;
    }

    private static Object resolveFieldValue(Field proxyField, ReflectedObject reflected) {
        String primaryName = proxyField.getName();

        // Build list of names to try: primary name, then @Alias names
        List<String> namesToTry = new ArrayList<>();
        namesToTry.add(primaryName);

        Alias alias = proxyField.getAnnotation(Alias.class);
        if (alias != null) {
            for (String alt : alias.value()) {
                namesToTry.add(alt);
            }
        }

        // Try field access for each name
        for (String name : namesToTry) {
            try {
                return reflected.field(name).get();
            } catch (ReflectionException ignored) {
                // field not found, try next
            }
        }

        // Try getter method fallback: getName() for field "name"
        String capitalized = Character.toUpperCase(primaryName.charAt(0)) + primaryName.substring(1);
        String getterName = "get" + capitalized;
        String isGetterName = "is" + capitalized;

        try {
            return reflected.method(getterName).params().call();
        } catch (ReflectionException ignored) {
        }

        try {
            return reflected.method(isGetterName).params().call();
        } catch (ReflectionException ignored) {
        }

        throw new ReflectionException(
                "Unable to resolve field '" + primaryName + "' on " + reflected.getTargetClass().getName()
                        + ". Tried fields: " + namesToTry + ", getters: [" + getterName + ", " + isGetterName + "]");
    }

    public static class MethodInterceptor {

        private final ReflectedObject reflected;

        MethodInterceptor(ReflectedObject reflected) {
            this.reflected = reflected;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @AllArguments Object[] args) {
            String primaryName = method.getName();

            // Build list of names to try
            List<String> namesToTry = new ArrayList<>();
            namesToTry.add(primaryName);

            Alias alias = method.getAnnotation(Alias.class);
            if (alias != null) {
                for (String alt : alias.value()) {
                    namesToTry.add(alt);
                }
            }

            // Try each method name
            for (String name : namesToTry) {
                try {
                    return reflected.method(name).call(args);
                } catch (ReflectionException ignored) {
                    // method not found, try next
                }
            }

            throw new ReflectionException(
                    "Unable to resolve method '" + primaryName + "' on " + reflected.getTargetClass().getName()
                            + ". Tried names: " + namesToTry);
        }

    }

}
