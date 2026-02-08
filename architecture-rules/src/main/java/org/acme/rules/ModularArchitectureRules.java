package org.acme.rules;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Modular architecture rules for automated validation.
 * 
 * This library should be imported as a test dependency in all
 * modular monoliths.
 * 
 * Usage:
 * 
 * <pre>
 * &#64;AnalyzeClasses(packages = "org.acme", importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *         @ArchTest
 *         static final ArchRule domain_isolation = ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE;
 * }
 * </pre>
 */
public final class ModularArchitectureRules {

        private ModularArchitectureRules() {
                // Utility class
        }

        // ==========================================
        // 1. LAYER ISOLATION
        // ==========================================

        /**
         * Domain cannot depend on Infrastructure.
         * 
         * The domain must be pure and free from framework dependencies like
         * JAX-RS, JPA, Hibernate, etc.
         */
        @ArchTest
        public static final ArchRule DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
                        .that().resideInAPackage("..domain..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(
                                        "..infrastructure..",
                                        "..infra..",
                                        "..persistence..",
                                        "..rest..",
                                        "..http..",
                                        "..adapter..")
                        .as("Domain must not depend on Infrastructure");

        /**
         * Domain cannot depend on infrastructure frameworks.
         */
        @ArchTest
        public static final ArchRule DOMAIN_NOT_DEPEND_ON_FRAMEWORKS = noClasses()
                        .that().resideInAPackage("..domain..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(
                                        "jakarta.ws.rs..",
                                        "jakarta.persistence..",
                                        "io.quarkus..",
                                        "org.hibernate..")
                        .as("Domain must not depend on frameworks (JAX-RS, JPA, Quarkus)");

        // ==========================================
        // 2. ACCESS CONTROL
        // ==========================================

        /**
         * Resources/Controllers must not inject Repositories directly.
         * 
         * Communication must go through the Application layer (Use Cases).
         */
        @ArchTest
        public static final ArchRule RESOURCES_NOT_INJECT_REPOSITORIES = noClasses()
                        .that().haveNameMatching(".*Resource")
                        .or().haveNameMatching(".*Controller")
                        .should().dependOnClassesThat()
                        .haveNameMatching(".*Repository")
                        .as("Resources must not inject Repositories directly");

        /**
         * Internal packages must not be accessed from outside the module.
         * 
         * Exception: Factory classes in the API package are allowed to
         * instantiate internal implementations.
         */
        @ArchTest
        public static final ArchRule INTERNAL_PACKAGES_NOT_EXPOSED = noClasses()
                        .that().resideOutsideOfPackage("..internal..")
                        .and().haveNameNotMatching(".*Factory")
                        .should().dependOnClassesThat()
                        .resideInAPackage("..internal..")
                        .as("Internal packages must not be accessed from outside (except factories)");

        // ==========================================
        // 3. CYCLES AND DEPENDENCIES
        // ==========================================

        /**
         * There must be no cycles between packages.
         */
        @ArchTest
        public static final ArchRule NO_CYCLES_BETWEEN_PACKAGES = slices().matching("org.acme.(*)..")
                        .should().beFreeOfCycles()
                        .as("There must be no cycles between packages");

        // ==========================================
        // 4. CODING CONVENTIONS
        // ==========================================

        /**
         * Infrastructure Services must have CDI scope (@ApplicationScoped).
         * 
         * Domain services are excluded since they must remain pure
         * (framework-agnostic).
         * 
         * Uses allowEmptyShould(true) because some modules may not have
         * infrastructure services (all ServiceImpl in domain package).
         */
        @ArchTest
        public static final ArchRule SERVICES_MUST_HAVE_CDI_SCOPE = classes()
                        .that().haveNameMatching(".*ServiceImpl")
                        .or().haveNameMatching(".*Service")
                        .and().areNotInterfaces()
                        .and().resideOutsideOfPackage("..domain..")
                        .should().beAnnotatedWith(ApplicationScoped.class)
                        .allowEmptyShould(true)
                        .as("Infrastructure Services must be @ApplicationScoped");

        /**
         * Write operations must be transactional.
         */
        @ArchTest
        public static final ArchRule WRITE_OPERATIONS_MUST_BE_TRANSACTIONAL = methods()
                        .that().haveNameMatching("create.*|update.*|delete.*|save.*|remove.*")
                        .and().areDeclaredInClassesThat().haveNameMatching(".*ServiceImpl")
                        .should().beAnnotatedWith(Transactional.class)
                        .as("Write operations must be @Transactional");

        /**
         * Adapters must not be abstract.
         * 
         * This is a placeholder rule that validates adapter classes are concrete.
         * In a real project, you would check that adapters implement specific port
         * interfaces.
         * 
         * Uses allowEmptyShould(true) for modules that may not have adapters.
         */
        @ArchTest
        public static final ArchRule ADAPTERS_MUST_IMPLEMENT_INTERFACE = classes()
                        .that().haveNameMatching(".*Adapter")
                        .should().notBeInterfaces()
                        .allowEmptyShould(true)
                        .as("Adapters must be concrete classes");
}
