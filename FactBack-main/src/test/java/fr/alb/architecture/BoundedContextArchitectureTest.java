package fr.alb.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the bounded-context boundaries described in
 * {@code docs/BOUNDED_CONTEXTS.md}.
 *
 * <p>Each business context ({@code billing}, {@code yard}, {@code berth},
 * {@code dd}, {@code bol}, {@code edi}, {@code gate}, {@code parties}, {@code ai})
 * is an island. Code in one context must not reach into another context's
 * {@code model}, {@code dao} or {@code resource} packages directly. When a
 * context genuinely needs to talk to another, it goes through that other
 * context's {@code api} package or a domain event.
 *
 * <p>The P0-2 migration physically moved the files but did not yet rewire the
 * dependencies — billing services still call yard entities directly, for
 * example. Rather than block all of that work here, the rules are frozen with
 * {@link FreezingArchRule}: existing violations are recorded in
 * {@code src/test/resources/archunit_store} and only <em>new</em> violations
 * fail the build. Clearing the frozen set happens incrementally as the
 * domain-event bus (P0-4) and context APIs land.
 *
 * <p>Scope: {@code fr.alb.platform} and {@code fr.alb.model} are shared
 * (infrastructure, legacy base classes) and exempt. The {@code ai} context
 * is a read-only consumer and also exempt.
 */
public class BoundedContextArchitectureTest {

    /**
     * Business contexts under {@code fr.alb.<context>} that currently have code.
     * Add a context here once it gains its first class — before that, rules
     * against it trigger ArchUnit's "empty should" guard.
     */
    private static final String[] CONTEXTS = {
            "billing", "yard", "berth", "dd", "bol", "edi", "parties", "equipment", "customs"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("fr.alb");
    }

    @Test
    void no_context_reaches_into_another_context_model() {
        for (String self : CONTEXTS) {
            for (String other : CONTEXTS) {
                if (self.equals(other)) continue;
                ArchRule rule = noClasses()
                        .that().resideInAPackage("fr.alb." + self + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("fr.alb." + other + ".model..")
                        .because("'" + self + "' must not reference entities of '" + other
                                + "' — go through fr.alb." + other + ".api or a domain event.")
                        .allowEmptyShould(true);
                FreezingArchRule.freeze(rule).check(classes);
            }
        }
    }

    @Test
    void no_context_reaches_into_another_context_dao() {
        for (String self : CONTEXTS) {
            for (String other : CONTEXTS) {
                if (self.equals(other)) continue;
                ArchRule rule = noClasses()
                        .that().resideInAPackage("fr.alb." + self + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("fr.alb." + other + ".dao..")
                        .because("'" + self + "' must not use DAO of '" + other + "' directly.")
                        .allowEmptyShould(true);
                FreezingArchRule.freeze(rule).check(classes);
            }
        }
    }

    @Test
    void no_context_reaches_into_another_context_resource() {
        for (String self : CONTEXTS) {
            for (String other : CONTEXTS) {
                if (self.equals(other)) continue;
                ArchRule rule = noClasses()
                        .that().resideInAPackage("fr.alb." + self + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("fr.alb." + other + ".resource..")
                        .because("'" + self + "' must not depend on REST resources of '" + other + "'.")
                        .allowEmptyShould(true);
                FreezingArchRule.freeze(rule).check(classes);
            }
        }
    }
}
