# Implementation Checklist & Guidelines

This document provides practical checklists, verification steps, and automation tools for implementing modular architecture.

> **Navigation**: Return to [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | See all patterns: [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) | [STATE-ISOLATION.md](./STATE-ISOLATION.md) | [CODING-PATTERNS.md](./CODING-PATTERNS.md) | [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md)

## Quick Reference (For LLMs)

**When to use this doc**: Starting new features, refactoring code, or verifying architecture compliance

**Key rules**:

- ✅ DO: Run detection commands before claiming compliance
- ✅ DO: Follow checklists when adding features or refactoring
- ✅ DO: Verify state isolation (duplicate table check) first
- ❌ DON'T: Skip verification steps
- ❌ DON'T: Commit without running detection commands

**Detection**: All detection commands are in the [Detection Commands](#detection-commands) section below

**See also**:

- [STATE-ISOLATION.md](./STATE-ISOLATION.md) - Entity naming conventions
- [CODING-PATTERNS.md](./CODING-PATTERNS.md) - Implementation patterns
- [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) - Architecture principles

## When to Read This Document

**Read this document when:**

- [ ] Starting a new feature implementation
- [ ] Refactoring existing code
- [ ] Verifying architecture compliance
- [ ] Running detection commands
- [ ] Setting up automated verification

**Skip this document if:**

- You're only learning about architecture principles (see [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) and [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md))
- You're only reading implementation patterns (see [CODING-PATTERNS.md](./CODING-PATTERNS.md))

## Table of Contents

- [When Adding New Features](#when-adding-new-features)
- [When Refactoring Existing Code](#when-refactoring-existing-code)
- [State Isolation Verification](#state-isolation-verification)
- [Common Anti-Patterns to Avoid](#common-anti-patterns-to-avoid)
- [Module Organization Patterns](#module-organization-patterns)
- [Detection Commands](#detection-commands)
- [Automated Verification](#automated-verification)

---

## When Adding New Features

Follow this checklist when implementing new functionality:

### 1. Identify the Correct Module

- [ ] Determine which domain the feature belongs to
- [ ] Verify the module exists or needs to be created
- [ ] Ensure feature aligns with module's bounded context
- [ ] Check if feature spans multiple modules (if yes, design communication)

### 2. Check Boundaries

- [ ] Feature doesn't violate module boundaries
- [ ] No direct dependencies on other modules' `domain-internal` code
- [ ] Communication uses port interfaces / gateways only
- [ ] No shared JPA entities

### 3. Design Communication

If cross-module interaction is needed:

- [ ] Define explicit Java interface (port) in `domain-api`
- [ ] Document the contract with clear types (records / value objects)
- [ ] Choose communication method (CDI port, REST client, messaging)
- [ ] Implement adapter in infrastructure module
- [ ] Add error handling and fallbacks

### 4. Implement with Patterns

- [ ] **Repository**: Implement `PanacheRepositoryBase`, `@ApplicationScoped` (see [CODING-PATTERNS.md](./CODING-PATTERNS.md#repository-pattern--panache-encapsulation))
- [ ] **Resource**: Keep lean (<20 lines), only call services (see [CODING-PATTERNS.md](./CODING-PATTERNS.md#jax-rs-resource-responsibilities--lean-pattern))
- [ ] **Service**: Use `@Transactional` for writes (see [CODING-PATTERNS.md](./CODING-PATTERNS.md#transaction-management))
- [ ] **Entity**: Use module-prefixed `@Table(name = "module_entity")` (see [STATE-ISOLATION.md](./STATE-ISOLATION.md#entity-naming-conventions))
- [ ] **Logging**: Add `Logger.getLogger(Class)` (see [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md#observability--monitoring))
- [ ] **Metrics**: Track relevant metrics (`@Counted`, `@Timed`) (see [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md#observability--monitoring))

### 5. Add Resilience

- [ ] `@CircuitBreaker` for external service calls
- [ ] `@Timeout` on all network calls
- [ ] `@Retry` with exponential backoff
- [ ] `@Fallback` for graceful degradation
- [ ] Error handling that doesn't cascade

### 6. Add Observability

- [ ] Structured logging with `Logger.getLogger()`
- [ ] OpenTelemetry tracing (auto-instrumented by Quarkus)
- [ ] Metrics for key operations
- [ ] Health check updates if needed (`@Readiness`)
- [ ] Error tracking

### 7. Verify State Isolation

**CRITICAL**: Run before committing. See [STATE-ISOLATION.md](./STATE-ISOLATION.md) for detailed guidelines.

**Quick check** (see [Detection Commands](#detection-commands) for full list):

```bash
# Check for duplicate table names (CRITICAL)
grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d
```

### 8. Test Independence

- [ ] Unit tests for service logic
- [ ] `@QuarkusTest` integration tests with only this module's dependencies
- [ ] E2E tests can run in isolation
- [ ] No tests depend on other modules' databases
- [ ] External dependencies mocked (`@InjectMock`, WireMock)

### 9. Documentation

- [ ] Update module README if needed
- [ ] Document new interfaces/contracts
- [ ] Add code examples for complex logic
- [ ] Update architecture diagrams if structure changed

---

## When Refactoring Existing Code

Follow this checklist when modifying existing code:

### 1. Preserve Boundaries

- [ ] Don't break existing module boundaries
- [ ] Maintain port interface stability (or version changes)
- [ ] Keep `domain-api` exports unchanged
- [ ] No new cross-module dependencies

### 2. Maintain Contracts

- [ ] Existing interfaces remain stable
- [ ] Add new optional fields instead of changing existing
- [ ] Version breaking changes appropriately
- [ ] Update documentation for contract changes

### 3. Improve Patterns

#### Fat Resource → Lean Resource

- [ ] Identify business logic in JAX-RS resource
- [ ] Move logic to service
- [ ] Update resource to call service
- [ ] Remove repository injections from resource
- [ ] Update tests (move business logic tests to service)

#### Direct EntityManager → Panache Repository

- [ ] Create `PanacheRepositoryBase` implementation
- [ ] Add `@ApplicationScoped`
- [ ] Add custom query methods with business names
- [ ] Update services to use repository
- [ ] Update tests

#### Missing Transactions → Add @Transactional

- [ ] Identify write operations without `@Transactional`
- [ ] Add `@Transactional` to service methods (see [CODING-PATTERNS.md](./CODING-PATTERNS.md#transaction-management))
- [ ] Ensure no nested `@Transactional` methods
- [ ] Test rollback behavior

### 4. Verify No Regressions

- [ ] Run module's unit tests
- [ ] Run module's `@QuarkusTest` integration tests
- [ ] Run module's E2E tests
- [ ] Check for compilation errors
- [ ] Verify detection commands still pass

### 5. Update Documentation

- [ ] Reflect changes in module contracts
- [ ] Update code comments
- [ ] Update README if behavior changed
- [ ] Document breaking changes in CHANGELOG

---

## State Isolation Verification

**MANDATORY**: Run this checklist before claiming State Isolation compliance.

### Step 1: Duplicate Table Detection (CRITICAL)

```bash
# Check for duplicate @Table names - MOST IMPORTANT CHECK
DUPLICATES=$(grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d)
if [ ! -z "$DUPLICATES" ]; then
  echo "❌ CRITICAL VIOLATION: Duplicate table names found"
  echo "$DUPLICATES"
  exit 1
fi
```

**Expected**: Empty output (no duplicates)

**FAILURE CRITERIA**: If ANY duplicates found, State Isolation is VIOLATED.

### Step 2: Cross-Module Repository Import Detection

```bash
# Check for imports of another module's persistence layer
VIOLATIONS=$(grep -rn 'import.*\.infra\.persistence\.' --include='*.java' . | grep -v '/test/')
if [ ! -z "$VIOLATIONS" ]; then
  echo "❌ Cross-module persistence imports found:"
  echo "$VIOLATIONS"
  exit 1
fi
```

**Expected**: Empty output (no cross-module persistence imports)

### Step 3: Named Datasource Verification

```bash
# Verify modules have dedicated datasource configurations
grep -n 'quarkus.datasource\.' src/main/resources/application.properties | head -20
```

**Expected**: Each module has its own named datasource

---

## Common Anti-Patterns to Avoid

### 1. Shared Database Access

❌ **NEVER** access another module's repository directly:

```java
// ❌ BAD
@ApplicationScoped
public class OrderService {
    @Inject
    CustomerRepository customerRepo; // Wrong module!
}
```

✅ **DO** use gateway interfaces for cross-module data access:

```java
// ✅ GOOD
@ApplicationScoped
public class OrderService {
    @Inject
    CustomerGateway customerGateway; // Port interface
}
```

### 2. Duplicate Table Names

```java
// ❌ BAD — same table name in different modules
@Table(name = "plans") // ordering module
@Table(name = "plans") // invoicing module — conflict!

// ✅ GOOD — module-prefixed
@Table(name = "ordering_plans")
@Table(name = "invoicing_plans")
```

### 3. Direct Class Dependencies

```java
// ❌ BAD
import org.acme.identity.domain.internal.UserServiceImpl;

// ✅ GOOD
import org.acme.identity.domain.api.UserGateway; // Public port
```

### 4. Fat JAX-RS Resources

```java
// ❌ BAD — 50+ lines of logic in resource
@GET @Path("/{id}")
public Response getInvoice(@PathParam("id") String id) {
    var invoice = repo.findById(id);
    // ... 50 lines of calculations and logic
}

// ✅ GOOD — resource delegates to service
@GET @Path("/{id}")
public InvoiceResponse getInvoice(@PathParam("id") String id) {
    return InvoiceResponse.from(invoiceService.getById(id));
}
```

### 5. Global State

```java
// ❌ BAD — global mutable state
public static final Map<String, Object> globalCache = new ConcurrentHashMap<>();

// ✅ GOOD — module-specific cache
@ApplicationScoped
public class OrderCacheService {
    private final Map<String, Order> cache = new ConcurrentHashMap<>();
}
```

---

## Module Organization Patterns

### Hexagonal Architecture Structure

Our modules follow hexagonal (ports & adapters) architecture:

```
domain-module/
├── domain-api/           # Public ports (interfaces, value objects)
│   └── src/main/java/
│       └── org/acme/module/domain/api/
│           ├── OrderService.java         # Port interface
│           ├── InventoryGateway.java      # Outgoing port
│           └── model/
│               ├── OrderId.java          # Value object
│               └── PlaceOrderCommand.java
├── domain-internal/      # Private implementation
│   └── src/main/java/
│       └── org/acme/module/domain/internal/
│           ├── OrderServiceImpl.java     # Service implementation
│           └── OrderCalculator.java      # Domain logic
└── pom.xml
```

```
infrastructure-module/
├── src/main/java/
│   └── org/acme/module/infra/
│       ├── persistence/                  # JPA entities + Panache repos
│       │   ├── OrderEntity.java
│       │   └── OrderRepository.java
│       ├── rest/                         # JAX-RS resources
│       │   ├── OrderResource.java
│       │   └── dto/
│       │       ├── OrderResponse.java
│       │       └── PlaceOrderRequest.java
│       ├── adapter/                      # Gateway adapters
│       │   └── InventoryGatewayAdapter.java
│       └── config/                       # CDI wiring
│           └── DomainBeanProducer.java
└── pom.xml
```

### When to Create Each Level

| Level                | Purpose                    | When to Create                        |
| -------------------- | -------------------------- | ------------------------------------- |
| **domain-api**       | Public ports               | Always — every module needs a public API |
| **domain-internal**  | Private implementation     | Always — domain logic lives here      |
| **infrastructure**   | Persistence, REST, adapters| Always — connects domain to framework |

---

## Detection Commands

### Quick Verification

Run these commands to detect common violations:

#### Check State Isolation

```bash
# 1. Duplicate table names (CRITICAL)
grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d

# 2. Cross-module persistence imports
grep -rn 'import.*\.infra\.persistence\.' --include='*.java' . | grep -v '/test/'

# 3. Which modules have duplicate tables
grep -rn '@Table' --include='*.java' . | \
  grep -oP '(\S+\.java):.*name\s*=\s*"([^"]*)"' | sort -t'"' -k2 | \
  awk -F'"' '{if($2==prev) print "❌ DUPLICATE: " $2 " in " $0; prev=$2}'
```

#### Check Resource Violations

```bash
# Resources with repository injections
grep -rn 'Repository' --include='*.java' . | grep -i 'resource\|Resource' | grep -v '/test/'

# Long resource methods (>30 lines)
grep -rn '@GET\|@POST\|@PUT\|@DELETE' --include='*.java' . | grep -i 'resource'
```

#### Check Transaction Usage

```bash
# Write operations without @Transactional
grep -rn '\.persist\|\.delete\|\.flush' --include='*.java' . | \
  grep -v '@Transactional\|/test/'

# Verify @Transactional is on service methods, not resources
grep -rn '@Transactional' --include='*.java' . | grep -i 'resource'
```

#### Check Repository Pattern

```bash
# Check repositories implement PanacheRepositoryBase
grep -rn 'class.*Repository' --include='*.java' . | \
  grep -v 'PanacheRepositoryBase\|/test/\|interface'

# Repositories without @ApplicationScoped
grep -rn 'class.*Repository' --include='*.java' . | \
  grep -v '@ApplicationScoped\|/test/\|interface'
```

#### Check Observability

```bash
# Services without logging
find . -name '*.java' -path '*/domain/*' | xargs grep -L 'Logger' | grep -i 'service\|usecase'

# Check for Fault Tolerance annotations
grep -rn '@CircuitBreaker\|@Retry\|@Timeout\|@Fallback' --include='*.java' .

# REST clients without timeout
grep -rn '@RegisterRestClient' --include='*.java' . | xargs grep -L '@Timeout'
```

#### Check Third-Party Integration

```bash
# Hardcoded API keys
grep -rn 'apiKey\|api-key\|API_KEY' --include='*.java' . | grep -v '@ConfigProperty\|//\|import'

# Clients without Fault Tolerance
grep -rn '@RegisterRestClient' --include='*.java' . | xargs grep -L '@CircuitBreaker\|@Retry'

# Cross-module client imports
grep -rn 'import.*\.infra\.client\.' --include='*.java' . | grep -v '/test/'
```

---

## Automated Verification

### ArchUnit Tests

ArchUnit is the recommended way to enforce architecture rules in Java:

```java
@AnalyzeClasses(packages = "org.acme", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureComplianceTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnInfrastructure =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infra..");

    @ArchTest
    static final ArchRule domainInternalShouldNotBeAccessedDirectly =
        noClasses()
            .that().resideOutsideOfPackage("..domain.internal..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.internal..");

    @ArchTest
    static final ArchRule resourcesShouldNotInjectRepositories =
        noClasses()
            .that().resideInAPackage("..infra.rest..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infra.persistence..");
}
```

### Pre-commit Hook Script

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash
echo "🔍 Running Architecture Verification..."

# 1. Check State Isolation (CRITICAL)
echo "Checking for duplicate table names..."
DUPLICATES=$(grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d)
if [ ! -z "$DUPLICATES" ]; then
  echo "❌ COMMIT BLOCKED: Duplicate table names found:"
  echo "$DUPLICATES"
  exit 1
fi

# 2. Check Cross-Module Imports
echo "Checking for cross-module persistence imports..."
CROSS_IMPORTS=$(grep -rn 'import.*\.infra\.persistence\.' --include='*.java' . | grep -v '/test/')
if [ ! -z "$CROSS_IMPORTS" ]; then
  echo "❌ COMMIT BLOCKED: Cross-module persistence imports found:"
  echo "$CROSS_IMPORTS"
  exit 1
fi

# 3. Check Resource Violations
echo "Checking for resources with repository injections..."
RESOURCE_REPOS=$(grep -rn 'Repository' --include='*.java' . | grep -i 'resource' | grep -v '/test/')
if [ ! -z "$RESOURCE_REPOS" ]; then
  echo "⚠️  Warning: Resources with repository injections found:"
  echo "$RESOURCE_REPOS"
fi

echo "✅ Architecture verification passed"
exit 0
```

Make it executable:

```bash
chmod +x .git/hooks/pre-commit
```

### CI/CD Integration

Add to your CI pipeline (`.github/workflows/architecture-check.yml`):

```yaml
name: Architecture Verification

on: [push, pull_request]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Check State Isolation
        run: |
          DUPLICATES=$(grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d)
          if [ ! -z "$DUPLICATES" ]; then
            echo "❌ Duplicate table names found"
            exit 1
          fi

      - name: Check Cross-Module Imports
        run: |
          VIOLATIONS=$(grep -rn 'import.*\.infra\.persistence\.' --include='*.java' . | grep -v '/test/')
          if [ ! -z "$VIOLATIONS" ]; then
            echo "❌ Cross-module persistence imports found"
            exit 1
          fi

      - name: Run ArchUnit Tests
        run: mvn test -pl bom -am -Dtest=ArchitectureComplianceTest
```

### Maven Integration

Run ArchUnit tests as part of the build:

```xml
<!-- pom.xml — add ArchUnit dependency -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

```bash
# Run architecture tests
mvn test -Dtest=ArchitectureComplianceTest

# Run all tests including architecture
mvn verify
```

---

## Quick Reference Cards

### New Feature Checklist

```
□ Identify correct module
□ Design communication (if cross-module — port interfaces)
□ Implement with patterns (PanacheRepo, JAX-RS Resource, CDI Service)
□ Add resilience (@CircuitBreaker, @Timeout, @Retry)
□ Add observability (Logger, @Counted, @Timed)
□ Verify state isolation (run detection commands)
□ Test independence (@QuarkusTest)
□ Document
```

### Refactoring Checklist

```
□ Preserve boundaries
□ Maintain contracts (domain-api stability)
□ Improve patterns (Resource → Lean, EntityManager → Panache)
□ Verify no regressions
□ Update documentation
```

### Pre-Commit Checklist

```
□ Run: grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d
□ Run: grep -rn 'import.*\.infra\.persistence\.' --include='*.java' . | grep -v '/test/'
□ All tests pass (mvn verify)
□ No compilation errors
□ Documentation updated
```

---

## Summary

### Implementation Guidelines

When implementing features:

1. Follow the 10 principles
2. Use established patterns (PanacheRepo, JAX-RS Resource, `@Transactional`)
3. Verify state isolation
4. Add resilience and observability
5. Test in isolation (`@QuarkusTest`)
6. Document changes

### Verification Steps

Before committing:

1. Run detection commands
2. Check for violations
3. Verify tests pass (`mvn verify`)
4. Update documentation

### Automation

Set up:

1. Pre-commit hooks (grep-based)
2. ArchUnit tests (compile-time)
3. CI/CD checks (GitHub Actions)
4. Monitoring and alerts

---

## Next Steps

- **Module Principles**: See [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md)
- **State Isolation**: See [STATE-ISOLATION.md](./STATE-ISOLATION.md)
- **Coding Patterns**: See [CODING-PATTERNS.md](./CODING-PATTERNS.md)
- **Resilience**: See [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md)

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
