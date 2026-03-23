package com.rmjtromp.reflection.mapping;

import com.rmjtromp.ReflectionKit;
import com.rmjtromp.reflection.ReflectedField;
import com.rmjtromp.reflection.ReflectedObject;
import com.rmjtromp.reflection.ReflectionException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProxyFactory {

    @SuppressWarnings("unchecked")
    static <T> T create(Object target, Class<T> proxyType) {
        ReflectedObject reflected = ReflectionKit.query(target);

        try {
            // Generate subclass with ByteBuddy, intercepting abstract methods
            MethodInterceptor interceptor = new MethodInterceptor(reflected, proxyType);

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

        private static final String[] GETTER_PREFIXES = {"get", "is", "has"};
        private static final String SETTER_PREFIX = "set";

        private final ReflectedObject reflected;
        private final Class<?> proxyType;

        MethodInterceptor(ReflectedObject reflected, Class<?> proxyType) {
            this.reflected = reflected;
            this.proxyType = proxyType;
        }

        @RuntimeType
        public Object intercept(@This Object proxy, @Origin Method method, @AllArguments Object[] args) {
            boolean fieldAccess = method.isAnnotationPresent(FieldAccess.class);
            Alias alias = method.getAnnotation(Alias.class);

            // Detect getter: 0 params, non-void return, recognized prefix
            if (args.length == 0 && method.getReturnType() != void.class) {
                String fieldName = extractFieldName(method.getName(), GETTER_PREFIXES);
                if (fieldName != null) {
                    return resolveGetter(method, fieldAccess, alias, fieldName);
                }
            }

            // Detect setter: exactly 1 param, "set" prefix
            if (args.length == 1) {
                String fieldName = extractFieldName(method.getName(), new String[]{SETTER_PREFIX});
                if (fieldName != null) {
                    return resolveSetter(proxy, method, args[0], fieldAccess, alias, fieldName);
                }
            }

            // Non-getter/setter: if @FieldAccess, resolve as field using method name or alias
            if (fieldAccess) {
                List<String> names = buildFieldNames(method.getName(), alias);
                if (args.length == 0 && method.getReturnType() != void.class) {
                    return tryFieldGet(names, method.getReturnType());
                } else if (args.length == 1) {
                    Object result = tryFieldSet(names, args[0]);
                    syncProxyFields(proxy, names);
                    return result;
                }
            }

            // Default: delegate to method on target
            return tryMethodCall(method, args, alias);
        }

        private Object resolveGetter(Method method, boolean fieldAccess, Alias alias, String fieldName) {
            List<String> fieldNames = alias != null ? aliasNames(alias) : singletonList(fieldName);

            // Try method first (unless @FieldAccess)
            if (!fieldAccess) {
                List<String> methodNames = buildMethodNames(method.getName(), alias);
                Object result = tryMethodCallOrNull(methodNames, new Object[0]);
                if (result != UNRESOLVED) {
                    return result;
                }
            }

            // Fall back to field
            return tryFieldGet(fieldNames, method.getReturnType());
        }

        private Object resolveSetter(Object proxy, Method method, Object value, boolean fieldAccess, Alias alias, String fieldName) {
            List<String> fieldNames = alias != null ? aliasNames(alias) : singletonList(fieldName);

            // Try method first (unless @FieldAccess)
            if (!fieldAccess) {
                List<String> methodNames = buildMethodNames(method.getName(), alias);
                Object result = tryMethodCallOrNull(methodNames, new Object[]{value});
                if (result != UNRESOLVED) {
                    return result;
                }
            }

            // Fall back to field, then sync proxy fields
            Object result = tryFieldSet(fieldNames, value);
            syncProxyFields(proxy, fieldNames);
            return result;
        }

        private Object tryMethodCall(Method method, Object[] args, Alias alias) {
            List<String> namesToTry = buildMethodNames(method.getName(), alias);

            for (String name : namesToTry) {
                try {
                    return reflected.method(name).call(args);
                } catch (ReflectionException ignored) {
                }
            }

            throw new ReflectionException(
                    "Unable to resolve method '" + method.getName() + "' on " + reflected.getTargetClass().getName()
                            + ". Tried names: " + namesToTry);
        }

        // Sentinel to distinguish "method returned null" from "method not found"
        private static final Object UNRESOLVED = new Object();

        private Object tryMethodCallOrNull(List<String> names, Object[] args) {
            for (String name : names) {
                try {
                    return reflected.method(name).call(args);
                } catch (ReflectionException ignored) {
                }
            }
            return UNRESOLVED;
        }

        private Object tryFieldGet(List<String> names, Class<?> expectedType) {
            for (String name : names) {
                try {
                    ReflectedField field = reflected.field(name);
                    Object value = field.get();
                    // Verify type compatibility (handles primitive <-> boxed)
                    if (value != null && !box(expectedType).isAssignableFrom(value.getClass())) {
                        continue;
                    }
                    return value;
                } catch (ReflectionException ignored) {
                }
            }

            throw new ReflectionException(
                    "Unable to resolve field for getter on " + reflected.getTargetClass().getName()
                            + ". Tried field names: " + names);
        }

        private Object tryFieldSet(List<String> names, Object value) {
            for (String name : names) {
                try {
                    ReflectedField field = reflected.field(name);
                    Class<?> fieldType = field.getField().getType();

                    // Check type compatibility
                    if (value != null && !box(fieldType).isAssignableFrom(value.getClass())) {
                        continue;
                    }
                    if (value == null && fieldType.isPrimitive()) {
                        continue;
                    }

                    field.set(value);
                    return null;
                } catch (ReflectionException ignored) {
                }
            }

            throw new ReflectionException(
                    "Unable to resolve field for setter on " + reflected.getTargetClass().getName()
                            + ". Tried field names: " + names);
        }

        /**
         * After a field is set on the target, sync any proxy fields that map to the same target field name(s).
         */
        private void syncProxyFields(Object proxy, List<String> targetFieldNames) {
            List<Field> proxyFields = collectProxyFields(proxyType);
            for (Field proxyField : proxyFields) {
                if (Modifier.isStatic(proxyField.getModifiers())) {
                    continue;
                }

                // Check if this proxy field maps to any of the target field names
                String primaryName = proxyField.getName();
                Alias fieldAlias = proxyField.getAnnotation(Alias.class);

                boolean matches = targetFieldNames.contains(primaryName);
                if (!matches && fieldAlias != null) {
                    for (String alt : fieldAlias.value()) {
                        if (targetFieldNames.contains(alt)) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    try {
                        Object value = resolveFieldValue(proxyField, reflected);
                        // Auto-wrap @Proxy typed fields
                        if (value != null && proxyField.getType().isAnnotationPresent(Proxy.class)) {
                            value = ReflectionKit.map(value).to(proxyField.getType());
                        }
                        proxyField.setAccessible(true);
                        proxyField.set(proxy, value);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private static String extractFieldName(String methodName, String[] prefixes) {
            for (String prefix : prefixes) {
                if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
                    char first = methodName.charAt(prefix.length());
                    if (Character.isUpperCase(first)) {
                        return Character.toLowerCase(first) + methodName.substring(prefix.length() + 1);
                    }
                }
            }
            return null;
        }

        private static List<String> buildMethodNames(String primaryName, Alias alias) {
            List<String> names = new ArrayList<>();
            names.add(primaryName);
            if (alias != null) {
                for (String alt : alias.value()) {
                    names.add(alt);
                }
            }
            return names;
        }

        private static List<String> buildFieldNames(String methodName, Alias alias) {
            if (alias != null) {
                return aliasNames(alias);
            }
            // Try stripping known prefixes, otherwise use method name as-is
            String fieldName = extractFieldName(methodName, GETTER_PREFIXES);
            if (fieldName == null) {
                fieldName = extractFieldName(methodName, new String[]{SETTER_PREFIX});
            }
            if (fieldName == null) {
                fieldName = methodName;
            }
            return singletonList(fieldName);
        }

        private static List<String> aliasNames(Alias alias) {
            List<String> names = new ArrayList<>();
            for (String alt : alias.value()) {
                names.add(alt);
            }
            return names;
        }

        private static List<String> singletonList(String value) {
            List<String> list = new ArrayList<>();
            list.add(value);
            return list;
        }

        private static final Map<Class<?>, Class<?>> PRIMITIVE_BOXES = new HashMap<Class<?>, Class<?>>() {{
            put(boolean.class, Boolean.class);
            put(byte.class, Byte.class);
            put(short.class, Short.class);
            put(char.class, Character.class);
            put(int.class, Integer.class);
            put(long.class, Long.class);
            put(float.class, Float.class);
            put(double.class, Double.class);
            put(void.class, Void.class);
        }};

        private static Class<?> box(Class<?> type) {
            return PRIMITIVE_BOXES.getOrDefault(type, type);
        }

    }

}
