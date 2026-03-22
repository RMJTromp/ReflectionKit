package com.rmjtromp.reflection;

import com.rmjtromp.ReflectionKit;
import lombok.Value;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ReflectedMethod {

    private final Class<?> targetClass;
    private final Object boundInstance;
    private final List<String> names;

    private Class<?> returnTypeFilter;
    private Class<?>[] paramTypesFilter;
    private boolean paramsConfigured;
    private Method resolvedMethod;

    ReflectedMethod(Class<?> targetClass, Object boundInstance, String... names) {
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
        this.boundInstance = boundInstance;
        this.names = Arrays.asList(names);
    }

    ReflectedMethod(Method method, Object boundInstance) {
        this.targetClass = method.getDeclaringClass();
        this.boundInstance = boundInstance;
        this.names = Collections.singletonList(method.getName());
        this.resolvedMethod = ReflectedClass.makeAccessible(method);
    }

    public ReflectedMethod type(Class<?> returnType) {
        this.returnTypeFilter = Objects.requireNonNull(returnType, "returnType");
        this.resolvedMethod = null;
        return this;
    }

    public ReflectedMethod params(Class<?>... paramTypes) {
        this.paramTypesFilter = paramTypes == null ? new Class<?>[0] : Arrays.copyOf(paramTypes, paramTypes.length);
        this.paramsConfigured = true;
        this.resolvedMethod = null;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T call(Object... args) {
        Class<?>[] requestedParamTypes = paramsConfigured ? paramTypesFilter : ReflectedClass.classesFromArgs(args);
        Method method = resolveMethod(requestedParamTypes);
        try {
            return (T) method.invoke(boundInstance, args);
        } catch (InvocationTargetException ite) {
            throw new ReflectionException("Method invocation failed: " + method, ite.getCause());
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new ReflectionException("Failed to invoke method: " + method, exception);
        }
    }

    public Method getMethod() {
        Class<?>[] requestedParamTypes = paramsConfigured ? paramTypesFilter : null;
        return resolveMethod(requestedParamTypes);
    }

    public List<ReflectedMethod> all() {
        Class<?>[] requestedParamTypes = paramsConfigured ? paramTypesFilter : null;
        List<MethodCandidate> candidates = findCandidates(requestedParamTypes);
        List<ReflectedMethod> methods = new ArrayList<>(candidates.size());
        for (MethodCandidate candidate : candidates) {
            methods.add(new ReflectedMethod(candidate.method, boundInstance));
        }
        return Collections.unmodifiableList(methods);
    }

    public ReflectedObject query(Object... args) {
        Object value = call(args);
        if (value == null) {
            throw new ReflectionException("Method returned null: " + getMethod());
        }
        return ReflectionKit.query(value);
    }

    public <T> Function<Object[], T> asFunction() {
        return args -> call((Object[]) args);
    }

    public <T> Supplier<T> asSupplier() {
        return this::call;
    }

    public <A extends Annotation> A annotation(Class<A> type) {
        A annotation = getMethod().getAnnotation(type);
        if (annotation == null) {
            throw new ReflectionException("Annotation not found: " + type.getName() + " on " + getMethod());
        }
        return annotation;
    }

    public boolean hasAnnotation(Class<? extends Annotation> type) {
        return getMethod().isAnnotationPresent(type);
    }

    private Method resolveMethod(Class<?>[] requestedParamTypes) {
        if (resolvedMethod != null && isCompatible(resolvedMethod, requestedParamTypes)) {
            return resolvedMethod;
        }

        List<MethodCandidate> candidates = findCandidates(requestedParamTypes);
        if (candidates.isEmpty()) {
            throw new ReflectionException(notFoundMessage(requestedParamTypes));
        }

        MethodCandidate selected = selectCandidate(candidates);
        resolvedMethod = ReflectedClass.makeAccessible(selected.method);
        return resolvedMethod;
    }

    private boolean isCompatible(Method method, Class<?>[] requestedParamTypes) {
        if (returnTypeFilter != null && !ReflectedClass.matchesType(method.getReturnType(), returnTypeFilter)) {
            return false;
        }
        if (!nameMatches(method.getName())) {
            return false;
        }
        return ReflectedClass.paramsMatch(method.getParameterTypes(), requestedParamTypes);
    }

    private List<MethodCandidate> findCandidates(Class<?>[] requestedParamTypes) {
        List<MethodCandidate> hierarchyMatches = new ArrayList<>();
        List<Class<?>> searchOrder = ReflectedClass.buildSearchOrder(targetClass);
        for (int rank = 0; rank < searchOrder.size(); rank++) {
            Class<?> clazz = searchOrder.get(rank);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!matches(method, requestedParamTypes)) {
                    continue;
                }
                hierarchyMatches.add(new MethodCandidate(method, rank));
            }
        }

        if (!hierarchyMatches.isEmpty()) {
            return hierarchyMatches;
        }

        List<MethodCandidate> interfaceMatches = new ArrayList<>();
        List<Class<?>> interfaces = collectInterfacesIncludingSuperclasses(targetClass);
        for (int rank = 0; rank < interfaces.size(); rank++) {
            Class<?> iface = interfaces.get(rank);
            for (Method method : iface.getDeclaredMethods()) {
                if (!matches(method, requestedParamTypes)) {
                    continue;
                }
                interfaceMatches.add(new MethodCandidate(method, 10_000 + rank));
            }
        }
        return interfaceMatches;
    }

    private static List<Class<?>> collectInterfacesIncludingSuperclasses(Class<?> start) {
        List<Class<?>> interfaces = new ArrayList<>();
        Set<Class<?>> visited = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (Class<?> clazz : ReflectedClass.buildSearchOrder(start)) {
            for (Class<?> iface : ReflectedClass.collectInterfaces(clazz)) {
                if (visited.add(iface)) {
                    interfaces.add(iface);
                }
            }
        }
        return interfaces;
    }

    private boolean matches(Method method, Class<?>[] requestedParamTypes) {
        if (method.isSynthetic() || method.isBridge()) {
            return false;
        }
        if (!nameMatches(method.getName())) {
            return false;
        }
        if (returnTypeFilter != null && !ReflectedClass.matchesType(method.getReturnType(), returnTypeFilter)) {
            return false;
        }
        return ReflectedClass.paramsMatch(method.getParameterTypes(), requestedParamTypes);
    }

    private boolean nameMatches(String methodName) {
        return names.isEmpty() || names.contains(methodName);
    }

    private MethodCandidate selectCandidate(List<MethodCandidate> candidates) {
        int bestRank = candidates.stream().mapToInt(candidate -> candidate.classRank).min().orElse(Integer.MAX_VALUE);
        List<MethodCandidate> sameRank = candidates.stream()
                .filter(candidate -> candidate.classRank == bestRank)
                .collect(Collectors.toList());

        if (sameRank.size() == 1) {
            return sameRank.get(0);
        }

        List<ScoredMethod> scored = new ArrayList<>(sameRank.size());
        for (MethodCandidate candidate : sameRank) {
            int score = 0;
            for (MethodCandidate other : sameRank) {
                if (candidate == other) {
                    continue;
                }
                if (isMoreSpecific(candidate.method.getParameterTypes(), other.method.getParameterTypes())) {
                    score++;
                }
            }
            scored.add(new ScoredMethod(candidate, score));
        }

        scored.sort(Comparator.comparingInt(ScoredMethod::getScore).reversed());
        if (scored.size() == 1) {
            return scored.get(0).getCandidate();
        }

        ScoredMethod top = scored.get(0);
        ScoredMethod second = scored.get(1);
        if (top.getScore() > second.getScore()) {
            return top.getCandidate();
        }

        throw new ReflectionException(ambiguousMessage(sameRank));
    }

    private static boolean isMoreSpecific(Class<?>[] left, Class<?>[] right) {
        if (left.length != right.length) {
            return false;
        }

        boolean strictlyMoreSpecific = false;
        for (int i = 0; i < left.length; i++) {
            Class<?> leftType = ReflectedClass.box(left[i]);
            Class<?> rightType = ReflectedClass.box(right[i]);
            if (!rightType.isAssignableFrom(leftType)) {
                return false;
            }
            if (!leftType.equals(rightType)) {
                strictlyMoreSpecific = true;
            }
        }

        return strictlyMoreSpecific;
    }

    private String notFoundMessage(Class<?>[] requestedParamTypes) {
        return "Unable to resolve method " + names
                + " on " + targetClass.getName()
                + " with params " + ReflectedClass.describeParams(requestedParamTypes)
                + (returnTypeFilter == null ? "" : " and return type " + returnTypeFilter.getName());
    }

    private String ambiguousMessage(List<MethodCandidate> candidates) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (MethodCandidate candidate : candidates) {
            joiner.add(" - " + candidate.method);
        }
        return "Ambiguous method resolution for names " + names + " on "
                + targetClass.getName() + ". Matches:" + System.lineSeparator()
                + joiner + System.lineSeparator()
                + "Call params(...) explicitly to disambiguate.";
    }

    @Value
    private static class MethodCandidate {
        Method method;
        int classRank;
    }

    @Value
    private static class ScoredMethod {
        MethodCandidate candidate;
        int score;
    }
}
