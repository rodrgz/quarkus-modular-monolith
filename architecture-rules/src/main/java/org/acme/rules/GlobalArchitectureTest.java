package org.acme.rules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Global Architecture Test.
 * <p>
 * This test is designed to be executed via 'dependenciesToScan' configuration
 * in the maven-surefire-plugin. It dynamically determines which package to scan
 * based on the 'archunit.target.package' system property.
 */
class GlobalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        String targetPackage = System.getProperty("archunit.target.package");
        if (targetPackage == null || targetPackage.isBlank()) {
            throw new IllegalStateException(
                    "System property 'archunit.target.package' is not set. " +
                            "This test expects to be run with -Darchunit.target.package=<package.name>");
        }

        System.out.println("[GlobalArchitectureTest] Scanning package: " + targetPackage);

        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(targetPackage);
    }

    // ==========================================
    // LAYER ISOLATION
    // ==========================================

    @Test
    @DisplayName("Domain must not depend on Infrastructure (Global)")
    void domain_must_not_depend_on_infrastructure() {
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE.check(classes);
    }

    @Test
    @DisplayName("Domain must not depend on frameworks (Global)")
    void domain_must_not_depend_on_frameworks() {
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
    }

    // ==========================================
    // ACCESS CONTROL
    // ==========================================

    @Test
    @DisplayName("Resources must not inject Repositories directly (Global)")
    void resources_must_not_inject_repositories() {
        ModularArchitectureRules.RESOURCES_NOT_INJECT_REPOSITORIES.check(classes);
    }

    @Test
    @DisplayName("Internal packages must not be exposed (Global)")
    void internal_packages_must_not_be_exposed() {
        ModularArchitectureRules.INTERNAL_PACKAGES_NOT_EXPOSED.check(classes);
    }

    // ==========================================
    // CYCLES AND DEPENDENCIES
    // ==========================================

    // Note: SlicesRuleDefinition might be tricky with generic 'classes' check if it
    // assumes package structure.
    // ModularArchitectureRules.NO_CYCLES_BETWEEN_PACKAGES uses
    // slices().matching("org.acme.(*)..")
    // We should check if 'classes' import respects this or if we need to adjust the
    // rule.
    // For now, let's try running it.
    @Test
    @DisplayName("No cycles between packages (Global)")
    void no_cycles_between_packages() {
        ModularArchitectureRules.NO_CYCLES_BETWEEN_PACKAGES.check(classes);
    }

    // ==========================================
    // CODING CONVENTIONS
    // ==========================================

    @Test
    @DisplayName("Services must have CDI scope (Global)")
    void services_must_have_cdi_scope() {
        ModularArchitectureRules.SERVICES_MUST_HAVE_CDI_SCOPE.check(classes);
    }

    @Test
    @DisplayName("Adapters must implement interface (Global)")
    void adapters_must_implement_interface() {
        ModularArchitectureRules.ADAPTERS_MUST_IMPLEMENT_INTERFACE.check(classes);
    }
}
