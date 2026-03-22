package com.rmjtromp.reflection;

import com.rmjtromp.ReflectionKit;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectedFieldAndClassTest {

    @Test
    void fieldReadAndWriteWorksAcrossHierarchy() {
        DerivedFixture fixture = new DerivedFixture();

        ReflectionKit.query(fixture).field("baseValue").set("changed");
        String value = ReflectionKit.query(fixture).field("baseValue").get(String.class);

        assertEquals("changed", value);
    }

    @Test
    void fieldAliasAndTypeFilterCanResolveMember() {
        DerivedFixture fixture = new DerivedFixture();

        String value = ReflectionKit.query(fixture)
                .field("doesNotExist", "aliasField")
                .type(CharSequence.class)
                .get(String.class);

        assertEquals("alias", value);
    }

    @Test
    void fieldOptionalReturnsEmptyWhenLookupFails() {
        Optional<String> value = ReflectionKit.query(DerivedFixture.class)
                .field("missingField")
                .getOptional();

        assertFalse(value.isPresent());
    }

    @Test
    void fieldAnnotationLookupAndPresenceChecksWork() {
        ReflectedField field = ReflectionKit.query(new DerivedFixture()).field("annotatedField");

        assertTrue(field.hasAnnotation(TestMarker.class));
        assertEquals("marker", field.annotation(TestMarker.class).value());
        assertThrows(ReflectionException.class, () -> field.annotation(Deprecated.class));
    }

    @Test
    void fieldQueryThrowsForNullValue() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new DerivedFixture()).field("nullableField").query()
        );

        assertTrue(exception.getMessage().contains("Field value is null"));
    }

    @Test
    void fieldRequiresAtLeastOneName() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(DerivedFixture.class).field()
        );

        assertEquals("At least one field name is required", exception.getMessage());
    }

    @Test
    void fieldsReturnsInheritedMembersAndIsUnmodifiable() {
        List<ReflectedField> fields = ReflectionKit.query(new DerivedFixture()).fields();
        Set<String> names = new HashSet<>();
        for (ReflectedField field : fields) {
            names.add(field.getField().getName());
        }

        assertTrue(names.contains("childOnly"));
        assertTrue(names.contains("aliasField"));
        assertTrue(names.contains("baseValue"));
        assertTrue(names.contains("annotatedField"));
        assertThrows(UnsupportedOperationException.class, () -> fields.add(null));
    }

    @Test
    void classHierarchyUtilitiesReturnExpectedTypes() {
        ReflectedClass reflectedClass = ReflectionKit.query(DerivedFixture.class);

        assertEquals(BaseFixture.class, reflectedClass.superclass().getTargetClass());

        List<ReflectedClass> directInterfaces = reflectedClass.interfaces();
        assertEquals(1, directInterfaces.size());
        assertEquals(ExtendedLabel.class, directInterfaces.get(0).getTargetClass());

        List<ReflectedClass> hierarchy = reflectedClass.hierarchy();
        Set<Class<?>> types = new HashSet<>();
        for (ReflectedClass type : hierarchy) {
            types.add(type.getTargetClass());
        }

        assertTrue(types.contains(DerivedFixture.class));
        assertTrue(types.contains(BaseFixture.class));
        assertTrue(types.contains(ExtendedLabel.class));
        assertTrue(types.contains(HasLabel.class));
    }

    @Test
    void superclassThrowsWhenNoNonObjectParentExists() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(BaseFixture.class).superclass()
        );

        assertTrue(exception.getMessage().contains("Class has no non-Object superclass"));
    }

    // --- Private/Protected Access ---

    @Test
    void privateFieldOnSameClassIsAccessible() {
        DerivedFixture fixture = new DerivedFixture();

        String value = ReflectionKit.query(fixture).field("aliasField").get(String.class);
        assertEquals("alias", value);
    }

    @Test
    void privateFieldFromParentClassIsAccessible() {
        DerivedFixture fixture = new DerivedFixture();

        String value = ReflectionKit.query(fixture).field("baseValue").get(String.class);
        assertEquals("base", value);
    }

    @Test
    void protectedFieldIsAccessible() {
        ProtectedFixture fixture = new ProtectedFixture();

        String value = ReflectionKit.query(fixture).field("protectedValue").get(String.class);
        assertEquals("protected", value);
    }

    // --- Static Fields ---

    @Test
    void staticFieldReadViaClassQuery() {
        StaticFixture.staticValue = "original";

        String value = ReflectionKit.query(StaticFixture.class).field("staticValue").get(String.class);
        assertEquals("original", value);
    }

    @Test
    void staticFieldWriteViaClassQuery() {
        ReflectionKit.query(StaticFixture.class).field("staticValue").set("changed");
        assertEquals("changed", StaticFixture.staticValue);
    }

    @Test
    void staticFieldReadViaInstanceQuery() {
        StaticFixture instance = new StaticFixture();
        StaticFixture.staticValue = "fromInstance";

        String value = ReflectionKit.query(instance).field("staticValue").get(String.class);
        assertEquals("fromInstance", value);
    }

    // --- Primitive Fields ---

    @Test
    void primitiveIntFieldReadAndWrite() {
        DerivedFixture fixture = new DerivedFixture();

        int value = ReflectionKit.query(fixture).field("childOnly").get();
        assertEquals(7, value);

        ReflectionKit.query(fixture).field("childOnly").set(42);
        int updated = ReflectionKit.query(fixture).field("childOnly").get();
        assertEquals(42, updated);
    }

    @Test
    void primitiveBooleanFieldReadAndWrite() {
        PrimitiveFixture fixture = new PrimitiveFixture();

        boolean value = ReflectionKit.query(fixture).field("flag").get();
        assertTrue(value);

        ReflectionKit.query(fixture).field("flag").set(false);
        boolean updated = ReflectionKit.query(fixture).field("flag").get();
        assertFalse(updated);
    }

    @Test
    void primitiveFieldAcceptsBoxedValue() {
        PrimitiveFixture fixture = new PrimitiveFixture();

        ReflectionKit.query(fixture).field("count").set(Integer.valueOf(99));
        int value = ReflectionKit.query(fixture).field("count").get();
        assertEquals(99, value);
    }

    @Test
    void typeFilterMatchesPrimitiveAndBoxedInterchangeably() {
        PrimitiveFixture fixture = new PrimitiveFixture();

        int viaInt = ReflectionKit.query(fixture).field("count").type(int.class).get();
        int viaInteger = ReflectionKit.query(fixture).field("count").type(Integer.class).get();
        assertEquals(viaInt, viaInteger);
    }

    // --- Field Not Found / Wrong Type ---

    @Test
    void fieldNotFoundThrowsWithMeaningfulMessage() {
        ReflectionException exception = assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new DerivedFixture()).field("nonexistent").get()
        );

        assertTrue(exception.getMessage().contains("Unable to resolve field"));
        assertTrue(exception.getMessage().contains("nonexistent"));
    }

    @Test
    void fieldSetWithWrongTypeThrows() {
        assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(new DerivedFixture()).field("baseValue").set(12345)
        );
    }

    @Test
    void fieldGetWithWrongCastTypeThrows() {
        assertThrows(
                ClassCastException.class,
                () -> ReflectionKit.query(new DerivedFixture()).field("baseValue").get(Integer.class)
        );
    }

    // --- getOptional edge cases ---

    @Test
    void getOptionalReturnsValueWhenFieldExistsAndNonNull() {
        DerivedFixture fixture = new DerivedFixture();
        Optional<String> value = ReflectionKit.query(fixture).field("baseValue").getOptional();
        assertTrue(value.isPresent());
        assertEquals("base", value.get());
    }

    @Test
    void getOptionalReturnsEmptyWhenFieldValueIsNull() {
        DerivedFixture fixture = new DerivedFixture();
        Optional<String> value = ReflectionKit.query(fixture).field("nullableField").getOptional();
        assertFalse(value.isPresent());
    }

    // --- Deep Inheritance (3+ levels) ---

    @Test
    void fieldAccessWorksThreeLevelsDeep() {
        GrandchildFixture fixture = new GrandchildFixture();

        String grandparentValue = ReflectionKit.query(fixture).field("baseValue").get(String.class);
        String parentValue = ReflectionKit.query(fixture).field("aliasField").get(String.class);
        String childValue = ReflectionKit.query(fixture).field("grandchildField").get(String.class);

        assertEquals("base", grandparentValue);
        assertEquals("alias", parentValue);
        assertEquals("grandchild", childValue);
    }

    @Test
    void hierarchyIncludesThreeLevels() {
        List<ReflectedClass> hierarchy = ReflectionKit.query(GrandchildFixture.class).hierarchy();
        Set<Class<?>> types = hierarchy.stream().map(ReflectedClass::getTargetClass).collect(Collectors.toSet());

        assertTrue(types.contains(GrandchildFixture.class));
        assertTrue(types.contains(DerivedFixture.class));
        assertTrue(types.contains(BaseFixture.class));
    }

    // --- Diamond Interface Inheritance ---

    @Test
    void diamondInterfaceHierarchyHasNoDuplicates() {
        List<ReflectedClass> hierarchy = ReflectionKit.query(DiamondFixture.class).hierarchy();
        List<Class<?>> types = hierarchy.stream().map(ReflectedClass::getTargetClass).collect(Collectors.toList());
        Set<Class<?>> uniqueTypes = new HashSet<>(types);

        assertEquals(types.size(), uniqueTypes.size(), "hierarchy() should not contain duplicates");
        assertTrue(uniqueTypes.contains(DiamondBase.class));
        assertTrue(uniqueTypes.contains(DiamondLeft.class));
        assertTrue(uniqueTypes.contains(DiamondRight.class));
    }

    // --- superclass() on interface ---

    @Test
    void superclassOnInterfaceThrows() {
        assertThrows(
                ReflectionException.class,
                () -> ReflectionKit.query(HasLabel.class).superclass()
        );
    }

    // --- interfaces() on class with no interfaces ---

    @Test
    void interfacesReturnsEmptyListWhenNoneImplemented() {
        List<ReflectedClass> ifaces = ReflectionKit.query(BaseFixture.class).interfaces();
        assertTrue(ifaces.isEmpty());
    }

    // --- fields()/methods() on leaf class ---

    @Test
    void fieldsOnSimpleClassReturnsOnlyDeclaredFields() {
        List<ReflectedField> fields = ReflectionKit.query(new BaseFixture()).fields();
        Set<String> names = fields.stream().map(f -> f.getField().getName()).collect(Collectors.toSet());

        assertTrue(names.contains("baseValue"));
        assertFalse(names.contains("class")); // no Object members
    }

    @Test
    void methodsExcludesObjectMethods() {
        List<ReflectedMethod> methods = ReflectionKit.query(new SimpleMethodFixture()).methods();
        Set<String> names = methods.stream().map(m -> m.getMethod().getName()).collect(Collectors.toSet());

        assertTrue(names.contains("myMethod"));
        assertFalse(names.contains("toString"));
        assertFalse(names.contains("hashCode"));
        assertFalse(names.contains("equals"));
    }

    // --- Full chaining ---

    @Test
    void fieldQueryChainedToMethodCall() {
        ChainFixture fixture = new ChainFixture();

        int length = ReflectionKit.query(fixture)
                .field("inner")
                .query()
                .method("length")
                .call();

        assertEquals(5, length);
    }

    // --- Final field ---

    @Test
    void finalFieldCanBeWritten() {
        FinalFieldFixture fixture = new FinalFieldFixture();

        // setAccessible(true) allows writing final fields on most JVMs
        assertDoesNotThrow(() ->
                ReflectionKit.query(fixture).field("finalValue").set("modified")
        );
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface TestMarker {
        String value();
    }

    private interface HasLabel {
        default String label() {
            return "label";
        }
    }

    private interface ExtendedLabel extends HasLabel {
    }

    private static class BaseFixture {
        private String baseValue = "base";

        @TestMarker("marker")
        private String annotatedField = "annotation";

        private String nullableField = null;
    }

    private static class DerivedFixture extends BaseFixture implements ExtendedLabel {
        private String aliasField = "alias";
        private int childOnly = 7;
    }

    private static class GrandchildFixture extends DerivedFixture {
        private String grandchildField = "grandchild";
    }

    private static class ProtectedFixture {
        protected String protectedValue = "protected";
    }

    private static class StaticFixture {
        private static String staticValue = "static";
    }

    private static class PrimitiveFixture {
        private boolean flag = true;
        private int count = 10;
    }

    private interface DiamondBase {
        default String base() { return "base"; }
    }

    private interface DiamondLeft extends DiamondBase {}

    private interface DiamondRight extends DiamondBase {}

    private static class DiamondFixture implements DiamondLeft, DiamondRight {}

    private static class SimpleMethodFixture {
        private String myMethod() { return "hello"; }
    }

    private static class ChainFixture {
        private String inner = "hello";
    }

    private static class FinalFieldFixture {
        private final String finalValue = "original";
    }
}
