package com.rmjtromp.reflection;

import com.rmjtromp.ReflectionKit;
import lombok.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public final class ReflectedConstructor {

    private final Class<?> targetClass;
    private Class<?>[] paramTypesFilter;
    private boolean paramsConfigured;
    private Constructor<?> resolvedConstructor;

    ReflectedConstructor(Class<?> targetClass) {
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
    }

    public ReflectedConstructor params(Class<?>... paramTypes) {
        this.paramTypesFilter = paramTypes == null ? new Class<?>[0] : Arrays.copyOf(paramTypes, paramTypes.length);
        this.paramsConfigured = true;
        this.resolvedConstructor = null;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T newInstance(Object... args) {
        Class<?>[] requestedParamTypes = paramsConfigured ? paramTypesFilter : ReflectedClass.classesFromArgs(args);
        Constructor<?> constructor = resolveConstructor(requestedParamTypes);
        try {
            return (T) constructor.newInstance(args);
        } catch (InvocationTargetException ite) {
            throw new ReflectionException("Constructor invocation failed: " + constructor, ite.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            throw new ReflectionException("Failed to invoke constructor: " + constructor, exception);
        }
    }

    public Constructor<?> getConstructor() {
        Class<?>[] requestedParamTypes = paramsConfigured ? paramTypesFilter : null;
        return resolveConstructor(requestedParamTypes);
    }

    public ReflectedObject query(Object... args) {
        Object value = newInstance(args);
        if (value == null) {
            throw new ReflectionException("Constructor returned null: " + getConstructor());
        }
        return ReflectionKit.query(value);
    }

    private Constructor<?> resolveConstructor(Class<?>[] requestedParamTypes) {
        if (resolvedConstructor != null && ReflectedClass.paramsMatch(resolvedConstructor.getParameterTypes(), requestedParamTypes)) {
            return resolvedConstructor;
        }

        List<Constructor<?>> candidates = new ArrayList<>();
        for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
            if (!ReflectedClass.paramsMatch(constructor.getParameterTypes(), requestedParamTypes)) {
                continue;
            }
            candidates.add(constructor);
        }

        if (candidates.isEmpty()) {
            throw new ReflectionException("Unable to resolve constructor on "
                    + targetClass.getName() + " with params " + ReflectedClass.describeParams(requestedParamTypes));
        }

        Constructor<?> selected = selectCandidate(candidates);
        resolvedConstructor = ReflectedClass.makeAccessible(selected);
        return resolvedConstructor;
    }

    private Constructor<?> selectCandidate(List<Constructor<?>> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        List<ScoredConstructor> scored = new ArrayList<>(candidates.size());
        for (Constructor<?> candidate : candidates) {
            int score = 0;
            for (Constructor<?> other : candidates) {
                if (candidate == other) {
                    continue;
                }
                if (isMoreSpecific(candidate.getParameterTypes(), other.getParameterTypes())) {
                    score++;
                }
            }
            scored.add(new ScoredConstructor(candidate, score));
        }

        scored.sort(Comparator.comparingInt(ScoredConstructor::getScore).reversed());
        if (scored.size() == 1) {
            return scored.get(0).getConstructor();
        }

        ScoredConstructor top = scored.get(0);
        ScoredConstructor second = scored.get(1);
        if (top.getScore() > second.getScore()) {
            return top.getConstructor();
        }

        throw new ReflectionException(ambiguousMessage(candidates));
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

    private String ambiguousMessage(List<Constructor<?>> constructors) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (Constructor<?> constructor : constructors) {
            joiner.add(" - " + constructor);
        }
        return "Ambiguous constructor resolution for " + targetClass.getName()
                + ". Matches:" + System.lineSeparator()
                + joiner + System.lineSeparator()
                + "Call params(...) explicitly to disambiguate.";
    }

    @Value
    private static class ScoredConstructor {
        Constructor<?> constructor;
        int score;
    }
}
