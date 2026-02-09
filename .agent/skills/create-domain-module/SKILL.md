---
name: Domain Module Generator
description: Creates complete domain modules following modular monolith architecture standards with hexagonal architecture, Maven module structure, JPA entities, Panache repositories, CDI services, JAX-RS resources, and ArchUnit compliance verification for Java 21 and Quarkus 3.31+.
---

# Domain Module Generator

Expert in creating domain modules for Quarkus modular monoliths with hexagonal architecture.

## When to Use

- Creating a new domain module from scratch
- User asks to "create a module", "scaffold a module", or "generate a module"
- User needs a new bounded context (billing, inventory, notifications, etc.)

## Requirements Gathering

Before generating code, ask:

1. **Module name** (kebab-case, e.g., "billing", "inventory", "notifications")
2. **Architecture pattern**:
   - Flat module (single domain, like billing)
   - Subdomain-based (multiple subdomains under one domain, like content)
3. **Initial entities** (comma-separated, e.g., "Customer, Order, Payment")
4. **External integrations** (third-party services? e.g., "Stripe, SendGrid")
5. **Async processing needs** (will it use messaging? yes/no)

## Architecture Decision Tree

```
Does the module have multiple distinct subdomains that could scale independently?
├─ YES → Use Subdomain-Based Pattern (Pattern 2)
│         Examples: Content (admin, catalog, processor), Commerce (cart, checkout, fulfillment)
└─ NO → Use Flat Module Pattern (Pattern 1)
          Examples: Billing, Identity, Notifications
```

**Guidelines**:
- **Flat Module**: Single cohesive domain with 3-8 entities
- **Subdomain-Based**: Multiple subdomains with 10+ entities or subdomains that could become separate services

## Folder Structure

### Pattern 1: Flat Module (Maven module)

```
{service-name}/domain-modules/{module-name}-domain-module/
├── src/main/java/{base.package}/{module}/domain/
│   ├── api/                          # Public API interfaces & DTOs
│   │   ├── {EntityName}Service.java  # Port interface
│   │   ├── {EntityName}ServiceFactory.java
│   │   └── dto/                      # Domain DTOs (records)
│   └── internal/                     # Private implementations
│       └── {EntityName}ServiceImpl.java
├── pom.xml
└── README.md

{service-name}/data-shared-modules/{module-name}-infrastructure-module/
├── src/main/java/{base.package}/{module}/infra/
│   ├── persistence/
│   │   ├── {EntityName}Entity.java   # JPA entity
│   │   └── {EntityName}Repository.java # Panache repository
│   ├── adapter/                      # Port implementations (outbound)
│   │   └── {ExternalService}Adapter.java
│   └── rest/                         # Inbound adapters (if module-specific)
│       ├── {EntityName}Resource.java  # JAX-RS resource
│       └── dto/
│           ├── {EntityName}Request.java
│           └── {EntityName}Response.java
├── pom.xml
└── README.md

{service-name}/infrastructure-module/src/main/java/.../infra/
├── config/
│   └── DomainBeanProducer.java       # CDI @Produces for domain beans
├── rest/
│   └── {EntityName}Resource.java     # JAX-RS resource (alternative location)
└── health/
    └── {ServiceName}HealthCheck.java
```

### Pattern 2: Subdomain-Based Module

```
{service-name}/domain-modules/
├── {subdomain-1}-domain-module/
│   └── src/main/java/.../domain/
│       ├── api/
│       └── internal/
├── {subdomain-2}-domain-module/
│   └── src/main/java/.../domain/
│       ├── api/
│       └── internal/
└── events-module/                    # Shared domain events
    └── src/main/java/.../events/
        ├── DomainEvent.java
        ├── {subdomain1}/
        └── {subdomain2}/

{service-name}/data-shared-modules/
├── {subdomain-1}-infrastructure-module/
│   └── src/main/java/.../infra/persistence/
└── {subdomain-2}-infrastructure-module/
    └── src/main/java/.../infra/persistence/
```

## Component Generation

### 1. Domain Service Interface (domain/api/)

```java
package com.example.{module}.domain.api;

import java.util.List;

/**
 * Port interface for {Module} operations.
 * Pure domain — no framework dependencies.
 */
public interface {EntityName}Service {
    {EntityName}DTO findById(String id);
    List<{EntityName}DTO> findByUserId(String userId);
    {EntityName}DTO create(Create{EntityName}Command command);
    {EntityName}DTO update(String id, Update{EntityName}Command command);
    void delete(String id);
}
```

**Key points**:
- No CDI, JPA, or Quarkus annotations
- Use records for DTOs and commands
- Define as interface (port)

### 2. Domain Service Implementation (domain/internal/)

```java
package com.example.{module}.domain.internal;

/**
 * Implementation of {EntityName} business logic.
 * Pure Java — NO framework annotations.
 * Dependencies injected via constructor.
 */
public class {EntityName}ServiceImpl implements {EntityName}Service {

    private final {EntityName}Gateway gateway;

    public {EntityName}ServiceImpl({EntityName}Gateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public {EntityName}DTO create(Create{EntityName}Command command) {
        // Pure business logic
        return gateway.save(command);
    }
}
```

**Key points**:
- No `@ApplicationScoped`, `@Inject`, or any framework annotation
- Constructor injection only
- Use Factory class for instantiation

### 3. Factory (domain/api/)

```java
package com.example.{module}.domain.api;

import com.example.{module}.domain.internal.{EntityName}ServiceImpl;

public class {EntityName}ServiceFactory {
    private {EntityName}ServiceFactory() {}

    public static {EntityName}Service create({EntityName}Gateway gateway) {
        return new {EntityName}ServiceImpl(gateway);
    }
}
```

### 4. Domain DTOs (domain/api/dto/)

```java
package com.example.{module}.domain.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record {EntityName}DTO(
    String id,
    String name,
    String description,
    BigDecimal amount,
    LocalDateTime createdAt
) {}

public record Create{EntityName}Command(
    String name,
    String description,
    BigDecimal amount
) {}
```

### 5. JPA Entity (infra/persistence/)

**CRITICAL**: Always use module-prefixed table names!

```java
package com.example.{module}.infra.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

// ✅ GOOD: Module-prefixed table name
@Entity
@Table(name = "{module}_{entity_name}")
public class {EntityName}Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    // JPA requires default constructor
    public {EntityName}Entity() {}

    public {EntityName}Entity(String name, String description, BigDecimal amount) {
        this.name = name;
        this.description = description;
        this.amount = amount;
    }

    // Getters, setters, equals/hashCode on ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        {EntityName}Entity that = ({EntityName}Entity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

**Naming rules**:
- Entity class: `{EntityName}Entity` (PascalCase + Entity suffix)
- Table name: `{module}_{entity_name}` (module prefix + snake_case)
- Never use generic table names like `plan`, `user`, `item`

### 6. Panache Repository (infra/persistence/)

```java
package com.example.{module}.infra.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class {EntityName}Repository implements PanacheRepositoryBase<{EntityName}Entity, String> {

    public List<{EntityName}Entity> findByUserId(String userId) {
        return list("userId", userId);
    }

    public {EntityName}Entity findByExternalId(String externalId) {
        return find("externalId", externalId).firstResult();
    }
}
```

**Key points**:
- Implement `PanacheRepositoryBase<Entity, IdType>` (NOT extend `PanacheRepository`)
- `@ApplicationScoped` required
- Use Panache query methods (`list`, `find`, `persist`, `delete`)
- Add custom finder methods with business meaning

### 7. CDI Bean Producer (infra/config/)

```java
package com.example.{module}.infra.config;

import com.example.{module}.domain.api.{EntityName}Service;
import com.example.{module}.domain.api.{EntityName}ServiceFactory;
import com.example.{module}.domain.api.{EntityName}Gateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Wires pure domain implementations into Quarkus CDI container.
 * Domain remains framework-free and testable.
 */
@ApplicationScoped
public class DomainBeanProducer {

    @Produces
    @ApplicationScoped
    public {EntityName}Service {entityName}Service({EntityName}Gateway gateway) {
        return {EntityName}ServiceFactory.create(gateway);
    }
}
```

### 8. JAX-RS Resource (infra/rest/)

Keep resources lean — delegates to services only!

```java
package com.example.{module}.infra.rest;

import com.example.{module}.domain.api.{EntityName}Service;
import com.example.{module}.domain.api.dto.*;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/{entity-name}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class {EntityName}Resource {

    private final {EntityName}Service service;

    @Inject
    public {EntityName}Resource({EntityName}Service service) {
        this.service = service;
    }

    @GET
    public Response findAll(@QueryParam("userId") String userId) {
        return Response.ok(service.findByUserId(userId)).build();
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") String id) {
        var result = service.findById(id);
        return result != null
            ? Response.ok(result).build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    public Response create(Create{EntityName}Request request) {
        var command = new Create{EntityName}Command(request.name(), request.description());
        var result = service.create(command);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
```

**Key points**:
- Only call services, never repositories
- Use `jakarta.ws.rs.core.Response` for HTTP responses
- Keep methods under 15 lines
- No business logic in resources

### 9. REST DTOs (infra/rest/dto/)

```java
// Request DTO
public record Create{EntityName}Request(
    String name,
    String description,
    BigDecimal amount
) {}

// Response DTO  
public record {EntityName}Response(
    String id,
    String name,
    String description,
    BigDecimal amount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

### 10. Application Configuration

**application.properties**:
```properties
# {Module} datasource (if using separate datasource)
quarkus.datasource.{module}.db-kind=postgresql
quarkus.datasource.{module}.jdbc.url=jdbc:postgresql://localhost:5432/{module}_db
quarkus.datasource.{module}.username=${MODULE_DB_USER:postgres}
quarkus.datasource.{module}.password=${MODULE_DB_PASSWORD:password}

# Hibernate ORM for {module}
quarkus.hibernate-orm.{module}.database.generation=none
quarkus.hibernate-orm.{module}.packages=com.example.{module}.infra.persistence

# Flyway for {module}
quarkus.flyway.{module}.migrate-at-start=true
quarkus.flyway.{module}.locations=classpath:db/migration/{module}
```

### 11. Maven Module POM

```xml
<project>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>{service-name}-domain-modules</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>{module-name}-domain-module</artifactId>
    <name>{Module Name} Domain Module</name>

    <dependencies>
        <!-- Domain module has NO framework dependencies -->
        <!-- Only shared domain events if needed -->
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>events-module</artifactId>
        </dependency>
    </dependencies>
</project>
```

## Common Anti-Patterns

| Anti-Pattern | ❌ Bad | ✅ Good |
|---|---|---|
| Generic table names | `@Table(name = "plan")` | `@Table(name = "billing_plan")` |
| Fat resources | Business logic in `@POST` method | Delegate to service: `service.create(command)` |
| Repo injection in resources | `@Inject OrderRepository repo` | `@Inject OrderService service` |
| Missing `@Transactional` | Multi-step persist without tx | Add `@Transactional` on infra write methods |
| CDI in domain | `@ApplicationScoped` + `@Inject` in domain | Pure constructor injection + factory pattern |
| Cross-module entity imports | `import ...identity.infra.persistence.UserEntity` | Use ID references or HTTP/event-based integration |

## Verification Commands

After generating, verify compliance:

```bash
# 1. Duplicate table names (CRITICAL) — expect empty
grep -rn "@Table" --include="*.java" | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d

# 2. No CDI in domain — expect empty
grep -rn "@ApplicationScoped\|@Inject\|@Produces" --include="*.java" **/domain/

# 3. No repos in resources — expect empty
grep -rn "Repository" --include="*Resource.java"

# 4. @Transactional on infra writes
grep -B2 "public.*\(create\|update\|delete\|save\|remove\)" --include="*ServiceImpl.java" -rn | grep -v "@Transactional"

# 5. Build
mvn clean compile -pl {module-path}
mvn test -pl {module-path}
```

## Generation Process

1. Gather requirements → decide pattern (flat vs subdomain)
2. Create Maven module with `pom.xml`
3. Generate domain API (interfaces, record DTOs, factory)
4. Generate domain implementation (pure Java)
5. Generate JPA entities + Panache repositories
6. Generate CDI bean producer
7. Generate JAX-RS resources + REST DTOs
8. Update `application.properties`
9. Run verification → report results

## Success Criteria

- ✅ Domain module has ZERO framework dependencies
- ✅ Module-prefixed table names (e.g., `billing_customer`)
- ✅ Lean resources (<15 lines per method), `@Transactional` on infra writes
- ✅ Public API via interfaces (`domain/api/`), internals hidden (`domain/internal/`)
- ✅ ArchUnit tests pass, Maven build succeeds
