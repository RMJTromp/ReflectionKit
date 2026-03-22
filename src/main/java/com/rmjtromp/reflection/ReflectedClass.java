package com.rmjtromp.reflection;

import lombok.Getter;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class ReflectedClass {

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

    @Getter
    private final Class<?> targetClass;
    private final Object boundInstance;

    public ReflectedClass(Class<?> targetClass) {
        this(targetClass, null);
    }

    ReflectedClass(Class<?> targetClass, Object boundInstance) {
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
        this.boundInstance = boundInstance;
    }

    public ReflectedField field(String... names) {
        if (names == null || names.length == 0) {
            throw new ReflectionException("At least one field name is required");
        }
        return new ReflectedField(targetClass, boundInstance, names);
    }

    public List<ReflectedField> fields() {
        List<ReflectedField> fields = new ArrayList<>();
        for (Class<?> clazz : buildSearchOrder(targetClass)) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(new ReflectedField(makeAccessible(field), boundInstance));
            }
        }
        return Collections.unmodifiableList(fields);
    }

    public ReflectedMethod method(String... names) {
        if (names == null || names.length == 0) {
            throw new ReflectionException("At least one method name is required");
        }
        return new ReflectedMethod(targetClass, boundInstance, names);
    }

    public List<ReflectedMethod> methods() {
        List<ReflectedMethod> methods = new ArrayList<>();
        for (Class<?> clazz : buildSearchOrder(targetClass)) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                methods.add(new ReflectedMethod(makeAccessible(method), boundInstance));
            }
        }
        for (Class<?> iface : collectInterfaces(targetClass)) {
            for (Method method : iface.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                methods.add(new ReflectedMethod(makeAccessible(method), boundInstance));
            }
        }
        return Collections.unmodifiableList(methods);
    }

    public ReflectedConstructor constructor() {
        return new ReflectedConstructor(targetClass);
    }

    public ReflectedClass superclass() {
        Class<?> superclass = targetClass.getSuperclass();
        if (superclass == null || superclass == Object.class) {
            throw new ReflectionException("Class has no non-Object superclass: " + targetClass.getName());
        }
        return new ReflectedClass(superclass, boundInstance);
    }

    public List<ReflectedClass> interfaces() {
        Class<?>[] interfaces = targetClass.getInterfaces();
        List<ReflectedClass> reflectedInterfaces = new ArrayList<>(interfaces.length);
        for (Class<?> iface : interfaces) {
            reflectedInterfaces.add(new ReflectedClass(iface, boundInstance));
        }
        return Collections.unmodifiableList(reflectedInterfaces);
    }

    public List<ReflectedClass> hierarchy() {
        LinkedHashSet<Class<?>> hierarchy = new LinkedHashSet<>();
        for (Class<?> clazz : buildSearchOrder(targetClass)) {
            hierarchy.add(clazz);
            hierarchy.addAll(collectInterfaces(clazz));
        }

        List<ReflectedClass> reflectedClasses = new ArrayList<>(hierarchy.size());
        for (Class<?> clazz : hierarchy) {
            reflectedClasses.add(new ReflectedClass(clazz, boundInstance));
        }
        return Collections.unmodifiableList(reflectedClasses);
    }

    Object getBoundInstance() {
        return boundInstance;
    }

    static List<Class<?>> buildSearchOrder(Class<?> start) {
        List<Class<?>> order = new ArrayList<>();
        Class<?> cursor = start;
        while (cursor != null && cursor != Object.class) {
            order.add(cursor);
            cursor = cursor.getSuperclass();
        }
        return order;
    }

    static List<Class<?>> collectInterfaces(Class<?> root) {
        List<Class<?>> interfaces = new ArrayList<>();
        Set<Class<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<Class<?>> queue = new ArrayDeque<>();

        Collections.addAll(queue, root.getInterfaces());
        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();
            if (!visited.add(iface)) {
                continue;
            }

            interfaces.add(iface);
            Collections.addAll(queue, iface.getInterfaces());
        }

        return interfaces;
    }

    static <T extends AccessibleObject> T makeAccessible(T member) {
        try {
            member.setAccessible(true);
            return member;
        } catch (RuntimeException exception) {
            throw new ReflectionException("Failed to make member accessible: " + member, exception);
        }
    }

    static Class<?> box(Class<?> type) {
        if (type == null) {
            return null;
        }
        return PRIMITIVE_BOXES.getOrDefault(type, type);
    }

    static boolean matchesType(Class<?> declaredType, Class<?> requestedType) {
        if (requestedType == null) {
            return true;
        }
        return box(requestedType).isAssignableFrom(box(declaredType));
    }

    static boolean paramsMatch(Class<?>[] declared, Class<?>[] requested) {
        if (requested == null) {
            return true;
        }
        if (declared.length != requested.length) {
            return false;
        }

        for (int i = 0; i < declared.length; i++) {
            Class<?> declaredType = declared[i];
            Class<?> requestedType = requested[i];

            if (requestedType == null) {
                if (declaredType.isPrimitive()) {
                    return false;
                }
                continue;
            }

            if (!box(declaredType).isAssignableFrom(box(requestedType))) {
                return false;
            }
        }
        return true;
    }

    static Class<?>[] classesFromArgs(Object... args) {
        if (args == null || args.length == 0) {
            return new Class<?>[0];
        }

        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            classes[i] = args[i] == null ? null : args[i].getClass();
        }
        return classes;
    }

    static String describeParams(Class<?>[] types) {
        if (types == null) {
            return "<any>";
        }
        if (types.length == 0) {
            return "()";
        }

        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(types[i] == null ? "null" : types[i].getSimpleName());
        }
        builder.append(")");
        return builder.toString();
    }
}
