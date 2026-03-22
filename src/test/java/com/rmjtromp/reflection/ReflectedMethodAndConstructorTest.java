package com.rmjtromp.reflection;

import com.rmjtromp.ReflectionKit;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectedMethodAndConstructorTest {

    @Test
    void methodCallResolvesBestMatchAcrossOverloadsAndHierarchy() {
        MethodChildFixture fixture = new MethodChildFixture();

        String intResult = ReflectionKit.query(fixture).method("overload").call(3);
        String longResult = ReflectionKit.query(fixture).method("overload").call(3L);

        assertEquals("integer", intResult);
        assertEquals("long", longResult);
    }

    @Test
    void methodParamsCanDisambiguateOverloads() {
        MethodChildFixture fixture = new MethodChildFixture();

        String result = ReflectionKit.query(fixture)
                .method("overload")
                .params(Number.class)
                .call(10);

        assertEquals("number", result);
    }

    @Test
    void methodCanResolveDefaultInterfaceMethod() {
        MethodChildFixture fixture = new MethodChildFixture();

        String value = ReflectionKit.query(fixture).method("fromInterface").call();
        assertEquals("iface", value);
    }

    @Test
    void methodAllReturnsAllMatchesAndIsUnmodifiable() {
        MethodChildFixture fixture = new MethodChildFixture();

        List<ReflectedMethod> methods = ReflectionKit.query(fixture).method("overload").all();
        assertEquals(3, methods.size());
        assertThrows(UnsupportedOperationException.class, () -> methods.add(null));
    }

    @Test
    void methodAnnotationLookupAndPresenceChecksWork() {
        ReflectedMethod method = ReflectionKit.query(new MethodBaseFixture()).method("annotated");

        assertTrue(method.hasAnnotation(TestMarker.class));
        assertEquals("method", method.annotation(TestMarker.class).value());
        assertThrows(ReflectionException.class, () -> method.annotation(Deprecated.class));
    }

    @Test
    void methodQueryReturnsReflectedObjectForNonNullResults() {
        ReflectedObject reflected = ReflectionKit.query(new MethodBaseFixture())
                .method("wrap")
                .query("hello");

        Integer length = reflected.method("length").call();
        assertEquals(Integer.valueOf(5), length);
    }

    @Test
    void methodQueryThrowsWhenMethodReturnsNull() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new MethodBaseFixture()).method("returnsNull").query()
        );

        assertTrue(exception.getMessage().contains("Method returned null"));
    }

    @Test
    void methodInvocationFailuresWrapCause() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new MethodBaseFixture()).method("boom").call()
        );

        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals("boom", exception.getCause().getMessage());
    }

    @Test
    void methodResolutionThrowsWhenAmbiguous() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new AmbiguousMethodFixture()).method("pick").call("value")
        );

        assertTrue(exception.getMessage().contains("Ambiguous method resolution"));
    }

    @Test
    void methodAdaptersAsFunctionAndSupplierWork() {
        ReflectedMethod withArg = ReflectionKit.query(new MethodChildFixture())
                .method("greet")
                .params(String.class);
        Function<Object[], String> function = withArg.asFunction();
        assertEquals("hello Ray", function.apply(new Object[]{"Ray"}));

        ReflectedMethod zeroArg = ReflectionKit.query(new MethodBaseFixture()).method("plain");
        Supplier<String> supplier = zeroArg.asSupplier();
        assertEquals("plain", supplier.get());
    }

    @Test
    void methodCanInvokeStaticViaClassQuery() {
        String value = ReflectionKit.query(MethodBaseFixture.class).method("staticEcho").call("x");
        assertEquals("S:x", value);
    }

    @Test
    void methodRequiresAtLeastOneName() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(MethodBaseFixture.class).method()
        );

        assertEquals("At least one method name is required", exception.getMessage());
    }

    @Test
    void constructorCanInstantiateUsingBestOverload() {
        ConstructorFixture reflected = ReflectionKit.query(ConstructorFixture.class)
                .constructor()
                .newInstance(4);

        assertEquals("integer", reflected.value);
    }

    @Test
    void constructorParamsCanDisambiguateOverloads() {
        ConstructorFixture reflected = ReflectionKit.query(ConstructorFixture.class)
                .constructor()
                .params(Number.class)
                .newInstance(4);

        assertEquals("number", reflected.value);
    }

    @Test
    void constructorQueryReturnsReflectedObject() {
        ReflectedObject reflected = ReflectionKit.query(ConstructorFixture.class)
                .constructor()
                .params(Integer.class)
                .query(7);

        String value = reflected.field("value").get(String.class);
        assertEquals("integer", value);
    }

    @Test
    void constructorInvocationFailuresWrapCause() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(ConstructorFixture.class).constructor().newInstance("fail")
        );

        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals("boom", exception.getCause().getMessage());
    }

    @Test
    void constructorResolutionThrowsWhenAmbiguous() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(AmbiguousConstructorFixture.class).constructor().newInstance("value")
        );

        assertTrue(exception.getMessage().contains("Ambiguous constructor resolution"));
    }

    // --- Private Method Access ---

    @Test
    void privateMethodIsCallable() {
        String value = ReflectionKit.query(new MethodBaseFixture()).method("plain").call();
        assertEquals("plain", value);
    }

    @Test
    void protectedMethodFromParentIsCallable() {
        String value = ReflectionKit.query(new ProtectedMethodChild()).method("protectedMethod").call();
        assertEquals("protected", value);
    }

    // --- Null Argument Handling ---

    @Test
    void methodCallWithNullArgMatchesObjectParam() {
        String result = ReflectionKit.query(new NullArgFixture()).method("acceptString").call((Object) null);
        assertEquals("null", result);
    }

    @Test
    void methodCallWithNullArgFailsForPrimitiveParam() {
        assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new NullArgFixture()).method("acceptInt").call((Object) null)
        );
    }

    @Test
    void constructorWithNullArgMatchesObjectParam() {
        NullArgConstructorFixture result = ReflectionKit.query(NullArgConstructorFixture.class)
                .constructor()
                .newInstance((Object) null);
        assertEquals("null", result.value);
    }

    // --- No-Arg Method/Constructor ---

    @Test
    void noArgMethodCallWithoutExplicitParams() {
        String value = ReflectionKit.query(new MethodBaseFixture()).method("plain").call();
        assertEquals("plain", value);
    }

    @Test
    void noArgMethodCallWithExplicitEmptyParams() {
        String value = ReflectionKit.query(new MethodBaseFixture()).method("plain").params().call();
        assertEquals("plain", value);
    }

    @Test
    void defaultConstructorWithoutExplicitParams() {
        ConstructorFixture result = ReflectionKit.query(ConstructorFixture.class)
                .constructor()
                .newInstance();
        assertEquals("empty", result.value);
    }

    @Test
    void defaultConstructorWithExplicitEmptyParams() {
        ConstructorFixture result = ReflectionKit.query(ConstructorFixture.class)
                .constructor()
                .params()
                .newInstance();
        assertEquals("empty", result.value);
    }

    // --- Method/Constructor Not Found ---

    @Test
    void methodNotFoundThrowsWithMeaningfulMessage() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new MethodBaseFixture()).method("nonexistent").call()
        );

        assertTrue(exception.getMessage().contains("Unable to resolve method"));
        assertTrue(exception.getMessage().contains("nonexistent"));
    }

    @Test
    void methodWithWrongParamTypesThrows() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new MethodBaseFixture())
                        .method("wrap")
                        .params(Integer.class)
                        .call(123)
        );

        assertTrue(exception.getMessage().contains("Unable to resolve method"));
    }

    @Test
    void constructorNotFoundThrowsWithMeaningfulMessage() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(ConstructorFixture.class)
                        .constructor()
                        .params(List.class)
                        .newInstance(Collections.emptyList())
        );

        assertTrue(exception.getMessage().contains("Unable to resolve constructor"));
    }

    // --- Return Type Filter ---

    @Test
    void returnTypeFilterSelectsCorrectOverload() {
        String result = ReflectionKit.query(new ReturnTypeFixture())
                .method("compute")
                .type(String.class)
                .call();
        assertEquals("string", result);
    }

    @Test
    void returnTypeFilterRejectsWhenNoMatch() {
        assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new MethodBaseFixture())
                        .method("plain")
                        .type(Integer.class)
                        .call()
        );
    }

    @Test
    void returnTypeFilterWithPrimitive() {
        int result = ReflectionKit.query(new ReturnTypeFixture())
                .method("compute")
                .type(int.class)
                .call(true);
        assertEquals(42, result);
    }

    // --- Method Alias Names ---

    @Test
    void methodAliasResolvesToSecondName() {
        String result = ReflectionKit.query(new MethodBaseFixture())
                .method("nonexistentMethod", "plain")
                .call();
        assertEquals("plain", result);
    }

    // --- params(null) ---

    @Test
    void paramsNullTreatedAsNoArgs() {
        String result = ReflectionKit.query(new MethodBaseFixture())
                .method("plain")
                .params((Class<?>[]) null)
                .call();
        assertEquals("plain", result);
    }

    @Test
    void constructorParamsNullTreatedAsNoArgs() {
        ConstructorFixture result = ReflectionKit.query(ConstructorFixture.class)
                .constructor()
                .params((Class<?>[]) null)
                .newInstance();
        assertEquals("empty", result.value);
    }

    // --- Deep Inheritance Methods ---

    @Test
    void methodFromGrandparentIsCallable() {
        String result = ReflectionKit.query(new MethodGrandchild()).method("plain").call();
        assertEquals("plain", result);
    }

    @Test
    void overriddenMethodCallsMostSpecificVersion() {
        String result = ReflectionKit.query(new OverrideChild()).method("value").call();
        assertEquals("child", result);
    }

    // --- Interface Method on Interface Query ---

    @Test
    void methodsOnInterfaceListsInterfaceMethods() {
        List<ReflectedMethod> methods = ReflectionKit.query(InterfaceFixture.class).methods();
        assertFalse(methods.isEmpty());

        boolean hasFromInterface = methods.stream()
                .anyMatch(m -> m.getMethod().getName().equals("fromInterface"));
        assertTrue(hasFromInterface);
    }

    // --- Abstract Class Constructor ---

    @Test
    void instantiatingAbstractClassThrows() {
        assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(AbstractFixture.class).constructor().newInstance()
        );
    }

    // --- Private Constructor ---

    @Test
    void privateConstructorIsAccessible() {
        SingletonFixture result = ReflectionKit.query(SingletonFixture.class)
                .constructor()
                .newInstance();
        assertEquals("singleton", result.value);
    }

    // --- Static Method via Class Query (already tested but adding primitive return) ---

    @Test
    void staticMethodWithPrimitiveReturn() {
        int result = ReflectionKit.query(StaticMethodFixture.class).method("add").call(2, 3);
        assertEquals(5, result);
    }

    // --- hasAnnotation returns false ---

    @Test
    void hasAnnotationReturnsFalseWhenAbsent() {
        ReflectedMethod method = ReflectionKit.query(new MethodBaseFixture()).method("plain");
        assertFalse(method.hasAnnotation(TestMarker.class));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface TestMarker {
        String value();
    }

    private interface InterfaceFixture {
        default String fromInterface() {
            return "iface";
        }
    }

    private static class MethodBaseFixture {
        private String overload(Number value) {
            return "number";
        }

        private String overload(Integer value) {
            return "integer";
        }

        private String plain() {
            return "plain";
        }

        private String wrap(String value) {
            return value;
        }

        @TestMarker("method")
        private String annotated() {
            return "annotated";
        }

        private String returnsNull() {
            return null;
        }

        private String boom() {
            throw new IllegalStateException("boom");
        }

        private static String staticEcho(String value) {
            return "S:" + value;
        }
    }

    private static class MethodChildFixture extends MethodBaseFixture implements InterfaceFixture {
        private String overload(Long value) {
            return "long";
        }

        private String greet(String value) {
            return "hello " + value;
        }
    }

    private static class AmbiguousMethodFixture {
        private String pick(Serializable value) {
            return "serializable";
        }

        private String pick(CharSequence value) {
            return "chars";
        }
    }

    private static class ConstructorFixture {
        private final String value;

        private ConstructorFixture() {
            this.value = "empty";
        }

        private ConstructorFixture(Number value) {
            this.value = "number";
        }

        private ConstructorFixture(Integer value) {
            this.value = "integer";
        }

        private ConstructorFixture(String fail) {
            throw new IllegalStateException("boom");
        }
    }

    private static class AmbiguousConstructorFixture {
        private AmbiguousConstructorFixture(Serializable value) {
        }

        private AmbiguousConstructorFixture(CharSequence value) {
        }
    }

    private static class ProtectedMethodParent {
        protected String protectedMethod() {
            return "protected";
        }
    }

    private static class ProtectedMethodChild extends ProtectedMethodParent {}

    private static class NullArgFixture {
        private String acceptString(String value) {
            return value == null ? "null" : value;
        }

        private String acceptInt(int value) {
            return String.valueOf(value);
        }
    }

    private static class NullArgConstructorFixture {
        private final String value;

        private NullArgConstructorFixture(String value) {
            this.value = value == null ? "null" : value;
        }
    }

    private static class ReturnTypeFixture {
        private String compute() {
            return "string";
        }

        private int compute(boolean unused) {
            return 42;
        }
    }

    private static class MethodGrandchild extends MethodChildFixture {}

    private static class OverrideParent {
        private String value() {
            return "parent";
        }
    }

    private static class OverrideChild extends OverrideParent {
        private String value() {
            return "child";
        }
    }

    private static abstract class AbstractFixture {}

    private static class SingletonFixture {
        private final String value;

        private SingletonFixture() {
            this.value = "singleton";
        }
    }

    private static class StaticMethodFixture {
        private static int add(int a, int b) {
            return a + b;
        }
    }
}
