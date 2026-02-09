# Architecture Rules Template

Template for `architecture-rules/` shared library with ArchUnit validation rules.

## Directory Structure

```
architecture-rules/
├── pom.xml
└── src/main/java/${BASE_PACKAGE}/rules/
    ├── ModularArchitectureRules.java
    └── GlobalArchitectureTest.java
```

## pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>${BASE_PACKAGE}</groupId>
        <artifactId>platform-bom</artifactId>
        <version>${BOM_VERSION}</version>
        <relativePath>../platform-bom</relativePath>
    </parent>

    <artifactId>architecture-rules</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>Architecture Rules</name>
    <description>Shared ArchUnit rules for modular architecture validation</description>

    <dependencies>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.enterprise</groupId>
            <artifactId>jakarta.enterprise.cdi-api</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>jakarta.transaction</groupId>
            <artifactId>jakarta.transaction-api</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

## ModularArchitectureRules.java

```java
package ${BASE_PACKAGE}.rules;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Modular architecture rules for automated validation.
 */
public final class ModularArchitectureRules {

    private ModularArchitectureRules() {}

    // ==========================================
    // 1. LAYER ISOLATION
    // ==========================================

    /**
     * Domain cannot depend on Infrastructure.
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
     */
    @ArchTest
    public static final ArchRule RESOURCES_NOT_INJECT_REPOSITORIES = noClasses()
            .that().haveNameMatching(".*Resource")
            .or().haveNameMatching(".*Controller")
            .should().dependOnClassesThat()
            .haveNameMatching(".*Repository")
            .as("Resources must not inject Repositories directly");

    /**
     * Internal packages must not be accessed from outside.
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
     * No cycles between packages.
     */
    @ArchTest
    public static final ArchRule NO_CYCLES_BETWEEN_PACKAGES = slices()
            .matching("${BASE_PACKAGE}.(*)..") // Replace with actual package
            .should().beFreeOfCycles()
            .as("There must be no cycles between packages");

    // ==========================================
    // 4. CODING CONVENTIONS
    // ==========================================

    /**
     * Services must have CDI scope.
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
     * Adapters must be concrete classes.
     */
    @ArchTest
    public static final ArchRule ADAPTERS_MUST_BE_CONCRETE = classes()
            .that().haveNameMatching(".*Adapter")
            .should().notBeInterfaces()
            .allowEmptyShould(true)
            .as("Adapters must be concrete classes");
}
```

## GlobalArchitectureTest.java

```java
package ${BASE_PACKAGE}.rules;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Global architecture test that can be inherited by service modules.
 * 
 * Services can extend this or use ModularArchitectureRules directly.
 */
@AnalyzeClasses(packages = "${BASE_PACKAGE}", 
                importOptions = ImportOption.DoNotIncludeTests.class)
public class GlobalArchitectureTest {

    @ArchTest
    static final ArchRule domain_not_depend_on_infrastructure = 
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE;

    @ArchTest
    static final ArchRule domain_not_depend_on_frameworks = 
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORKS;

    @ArchTest
    static final ArchRule resources_not_inject_repositories = 
        ModularArchitectureRules.RESOURCES_NOT_INJECT_REPOSITORIES;

    @ArchTest
    static final ArchRule internal_packages_not_exposed = 
        ModularArchitectureRules.INTERNAL_PACKAGES_NOT_EXPOSED;

    @ArchTest
    static final ArchRule no_cycles = 
        ModularArchitectureRules.NO_CYCLES_BETWEEN_PACKAGES;

    @ArchTest
    static final ArchRule services_must_have_cdi_scope = 
        ModularArchitectureRules.SERVICES_MUST_HAVE_CDI_SCOPE;

    @ArchTest
    static final ArchRule write_operations_transactional = 
        ModularArchitectureRules.WRITE_OPERATIONS_MUST_BE_TRANSACTIONAL;
}
```

## Usage in Service Modules

Add to infrastructure-module's `pom.xml`:

```xml
<dependency>
    <groupId>${BASE_PACKAGE}</groupId>
    <artifactId>architecture-rules</artifactId>
    <scope>test</scope>
</dependency>
```

Create `src/test/java/.../ArchitectureTest.java`:

```java
@AnalyzeClasses(packages = "${BASE_PACKAGE}.{service}", 
                importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_isolation = 
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE;
    
    // ... include all relevant rules
}
```
