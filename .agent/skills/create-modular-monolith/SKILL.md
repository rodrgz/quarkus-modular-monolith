---
name: create-modular-monolith
description: Creates complete Quarkus modular monolith projects with hexagonal architecture, centralized BOM, shared domain modules, and ArchUnit enforcement. Use when creating a new modular monolith project, scaffolding a Quarkus application with module boundaries, or when the user asks to "create modular monolith", "scaffold quarkus project", "new modular architecture", or "create project with hexagonal architecture".
---

# Modular Monolith Project Generator

Creates production-ready Quarkus modular monolith projects that can evolve into microservices without costly rewrites.

## When to Use

- Creating a new modular monolith project from scratch
- User asks for "modular monolith", "hexagonal architecture project", "quarkus modular"
- Scaffolding a project with proper module boundaries
- Setting up shared domain modules between services

## Requirements Gathering

Before generating, ask the user:

1. **Project name** (kebab-case, e.g., "order-management")
2. **Base package** (e.g., "com.company.project")
3. **Initial services** (comma-separated, e.g., "commerce, invoicing")
4. **Shared domain modules** (optional, e.g., "inventory, customer")
5. **Database type** (postgresql, mysql, h2)

## Architecture Overview

```
project-name/
├── platform-bom/                    # Centralized versions (parent for all)
├── architecture-rules/              # ArchUnit rules library
├── {service-1}/                     # First modular service
│   ├── domain-modules/
│   │   └── {domain}-domain-module/
│   ├── application-module/
│   └── infrastructure-module/
├── {service-2}/                     # Second modular service
└── pom.xml                          # Root aggregator
```

## Generation Process

### Step 1: Create Root Structure

Generate root `pom.xml` (aggregator only, no parent):

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>{base.package}</groupId>
    <artifactId>{project-name}-root</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>platform-bom</module>
        <module>architecture-rules</module>
        <!-- services added here -->
    </modules>
</project>
```

### Step 2: Create platform-bom

The BOM is the **single source of truth** for all versions.

Key sections:
- **Properties**: Java version, Quarkus version, internal module versions
- **DependencyManagement**: Framework BOMs, internal modules, test libraries
- **PluginManagement**: compiler, surefire, jandex, quarkus-maven-plugin

Reference: [platform-bom-template.md](references/platform-bom-template.md)

### Step 3: Create architecture-rules

Shared library with ArchUnit rules for validation.

**Required Rules**:

| Rule | Purpose |
|------|---------|
| `DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE` | Domain isolation |
| `DOMAIN_NOT_DEPEND_ON_FRAMEWORKS` | Framework agnostic domain |
| `RESOURCES_NOT_INJECT_REPOSITORIES` | Layered architecture |
| `INTERNAL_PACKAGES_NOT_EXPOSED` | Encapsulation |
| `NO_CYCLES_BETWEEN_PACKAGES` | No circular deps |
| `WRITE_OPERATIONS_MUST_BE_TRANSACTIONAL` | Transaction safety |

Reference: [architecture-rules-template.md](references/architecture-rules-template.md)

### Step 4: Create Services

For each service, generate hexagonal structure:

```
{service}/
├── pom.xml              # Parent: platform-bom
├── domain-modules/
│   ├── pom.xml
│   └── {domain}-domain-module/
│       ├── pom.xml
│       └── src/main/java/{package}/domain/
│           ├── api/              # Public interfaces
│           ├── dto/              # Shared DTOs
│           └── internal/         # Hidden implementations
├── application-module/
│   ├── pom.xml
│   └── src/main/java/{package}/application/
│       └── usecase/              # Business use cases
└── infrastructure-module/
    ├── pom.xml
    └── src/main/java/{package}/infrastructure/
        ├── rest/                 # JAX-RS resources
        ├── persistence/          # JPA entities, repositories
        └── config/               # Quarkus configuration
```

### Step 5: Configure Dependencies

**Service pom.xml pattern**:

```xml
<parent>
    <groupId>{base.package}</groupId>
    <artifactId>platform-bom</artifactId>
    <version>2026.02.0</version>
    <relativePath>../platform-bom</relativePath>
</parent>
```

**Infrastructure-module dependencies**:
- Domain modules (from same service)
- Application module
- Quarkus extensions (rest, hibernate-orm-panache, jdbc-postgresql)
- architecture-rules (test scope)

### Step 6: Generate ArchUnit Test

Each deployable module needs `ArchitectureTest.java`:

```java
@AnalyzeClasses(packages = "{base.package}", 
                importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_not_depend_on_infrastructure = 
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE;

    @ArchTest
    static final ArchRule domain_not_depend_on_frameworks = 
        ModularArchitectureRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORKS;

    // ... all rules
}
```

### Step 7: Generate Configuration

Create `application.properties` with profiles:

```properties
# Default (H2 for dev)
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:{service}

# Logging with trace context
quarkus.log.console.format=%d{HH:mm:ss} %-5p traceId=%X{traceId} [%c{2.}] %s%e%n

# Remote profile for microservice extraction
%remote.inventory.service.url=http://localhost:8082
```

### Step 8: Generate README

Include:
- Architecture overview with Mermaid diagrams
- Quick start commands
- Service ports
- Test commands
- Contributing guidelines

## Shared Domain Modules

For modules shared between services:

1. **Create in first service's domain-modules/**
2. **Define in platform-bom** with version
3. **Import in other services** via dependency

```xml
<!-- In platform-bom -->
<dependency>
    <groupId>{base.package}.{service}</groupId>
    <artifactId>{domain}-domain-module</artifactId>
    <version>${{domain}-domain-module.version}</version>
</dependency>
```

## Output Files

After generation:

```
{project-name}/
├── pom.xml
├── platform-bom/pom.xml
├── architecture-rules/
│   ├── pom.xml
│   └── src/main/java/.../ModularArchitectureRules.java
├── {service-1}/
│   ├── pom.xml
│   ├── domain-modules/{domain}/...
│   ├── application-module/...
│   └── infrastructure-module/
│       ├── src/main/java/.../rest/{Entity}Resource.java
│       ├── src/test/java/.../ArchitectureTest.java
│       └── src/main/resources/application.properties
└── README.md
```

## Verification Commands

After generation:

```bash
# Build entire project
mvn clean install

# Run specific service
mvn quarkus:dev -pl {service}/infrastructure-module

# Run architecture tests
mvn test -Dtest=ArchitectureTest
```

## Best Practices Enforced

- ✅ Domain remains pure (no framework annotations)
- ✅ Hexagonal architecture (Ports & Adapters)
- ✅ Centralized version management (BOM)
- ✅ Architecture validation (ArchUnit in CI)
- ✅ Profile-based microservice extraction
- ✅ OpenTelemetry ready (trace context in logs)

## Anti-Patterns Prevented

- ❌ Domain depending on infrastructure
- ❌ Resources injecting repositories directly
- ❌ Circular dependencies between packages
- ❌ Write operations without @Transactional
- ❌ Internal packages exposed publicly
