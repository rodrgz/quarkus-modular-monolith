package org.acme.order;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.acme.rules.ModularArchitectureRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture Tests for Order Service.
 * 
 * Validates modular boundaries and architecture rules defined in the
 * architecture-rules library.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.acme");
    }

    // ==========================================
    // LAYER ISOLATION
    // ==========================================

    @Test
    @DisplayName("Domain must not depend on Infrastructure")
    void domain_must_not_depend_on_infrastructure() {
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE.check(classes);
    }

    @Test
    @DisplayName("Domain must not depend on frameworks")
    void domain_must_not_depend_on_frameworks() {
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
    }

    // ==========================================
    // ACCESS CONTROL
    // ==========================================

    @Test
    @DisplayName("Resources must not inject Repositories directly")
    void resources_must_not_inject_repositories() {
        ModularArchitectureRules.RESOURCES_NOT_INJECT_REPOSITORIES.check(classes);
    }

    @Test
    @DisplayName("Internal packages must not be exposed")
    void internal_packages_must_not_be_exposed() {
        ModularArchitectureRules.INTERNAL_PACKAGES_NOT_EXPOSED.check(classes);
    }

    // ==========================================
    // CYCLES AND DEPENDENCIES
    // ==========================================

    @Test
    @DisplayName("No cycles between packages")
    void no_cycles_between_packages() {
        ModularArchitectureRules.NO_CYCLES_BETWEEN_PACKAGES.check(classes);
    }

    // ==========================================
    // CODING CONVENTIONS
    // ==========================================

    @Test
    @DisplayName("Services must have CDI scope")
    void services_must_have_cdi_scope() {
        ModularArchitectureRules.SERVICES_MUST_HAVE_CDI_SCOPE.check(classes);
    }

    @Test
    @DisplayName("Adapters must implement interface")
    void adapters_must_implement_interface() {
        ModularArchitectureRules.ADAPTERS_MUST_IMPLEMENT_INTERFACE.check(classes);
    }
}
