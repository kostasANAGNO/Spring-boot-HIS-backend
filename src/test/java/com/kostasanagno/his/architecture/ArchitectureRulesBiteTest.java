package com.kostasanagno.his.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A rule that never fails is worse than no rule. This runs EVERY rule of {@link HisArchRules} against the sample
 * classes in {@code com.example.archfixtures} (each deliberately violates them) and requires a failure. Adding a rule
 * without adding a sample that breaks it makes this test fail.
 */
class ArchitectureRulesBiteTest {

    private static final JavaClasses BAD_CODE = new ClassFileImporter().importPackages("com.example.archfixtures");

    static Stream<Arguments> allRules() {
        return ruleFields().map(field -> Arguments.of(field.getName(), rule(field)));
    }

    @ParameterizedTest(name = "{0} fails on bad code")
    @MethodSource("allRules")
    void everyRuleDetectsItsViolation(String name, ArchRule rule) {
        Throwable failure = catchThrowable(() -> rule.check(BAD_CODE));
        assertThat(failure)
                .as("rule %s should reject the sample classes", name)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated");
    }

    @Test
    void theSamplesAreActuallyThere() {
        assertThat(BAD_CODE.stream().map(type -> type.getSimpleName()).toList())
                .contains("WrongController", "BrokenEntity", "BrokenDto", "BrokenServiceImpl", "BrokenRepository",
                        "BrokenMapper", "BrokenException", "Gateway");
    }

    @Test
    void thereAreRulesToCheck() {
        assertThat(ruleFields().count()).isGreaterThanOrEqualTo(20);
    }

    private static Stream<Field> ruleFields() {
        List<Field> fields = Arrays.stream(HisArchRules.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ArchTest.class))
                .filter(field -> ArchRule.class.isAssignableFrom(field.getType()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .toList();
        return fields.stream();
    }

    private static ArchRule rule(Field field) {
        try {
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
