# State Isolation (Principle 8)

⚠️ **CRITICAL**: This is the most frequently violated principle in modular architecture. Read carefully before creating any JPA entities.

> **Navigation**: Return to [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | See also [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) | [CODING-PATTERNS.md](./CODING-PATTERNS.md)

## Quick Reference (For LLMs)

**When to use this doc**: Creating/modifying JPA entities, Flyway migrations, or working with databases

**Key rules**:

- ✅ DO: Prefix table names with module name (e.g., `ordering_orders`, not `orders`)
- ✅ DO: Give each module its own named datasource
- ✅ DO: Use gateway interfaces/APIs for cross-module data needs
- ❌ DON'T: Create duplicate `@Table(name = "x")` across modules (CRITICAL VIOLATION)
- ❌ DON'T: Access other modules' databases directly

**Detection**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for detection commands

**See also**:

- [CODING-PATTERNS.md](./CODING-PATTERNS.md) - Repository and transaction patterns
- [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) - Verification steps

## When to Read This Document

**Read this document when:**

- [ ] Creating or modifying JPA entities
- [ ] Setting up datasource connections
- [ ] Creating Flyway migrations
- [ ] Planning cross-module data access
- [ ] Verifying state isolation compliance

**Skip this document if:**

- You're only implementing services/resources (see [CODING-PATTERNS.md](./CODING-PATTERNS.md))
- You're only designing module boundaries (see [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md))
- You're only integrating external APIs (see [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md))

**CRITICAL**: Read this before creating any JPA entities!

## Table of Contents

- [Definition](#definition)
- [Critical Violations](#critical-violations)
- [Rules for AI Agents](#rules-for-ai-agents)
- [Entity Naming Conventions](#entity-naming-conventions)
- [Code Examples](#code-examples)
- [Detection Commands](#detection-commands)
- [Migration Strategies](#migration-strategies)

---

## Definition

**State Isolation**: Each module owns and manages its own state without sharing databases or state with other modules.

This principle ensures:

- Clear data ownership
- Independent schema evolution
- Deployment flexibility
- Migration independence
- No accidental data coupling

---

## Critical Violations

### ❌ FORBIDDEN: Duplicate Table Names Across Modules

**The most critical violation**: Multiple modules defining entities with the same `@Table(name = "...")`.

```java
// ❌ CRITICAL VIOLATION: Same table name in different modules
// ordering-domain-module/.../OrderEntity.java
@Entity
@Table(name = "plans")
public class OrderPlan { /* ... */ }

// invoicing-domain-module/.../InvoicePlanEntity.java
@Entity
@Table(name = "plans")  // ❌ VIOLATION! Same table name
public class InvoicePlan { /* ... */ }
```

**Problems:**

- Both modules write to the same database table
- Unclear data ownership
- Flyway migration conflicts
- Cannot deploy modules independently
- Schema changes affect multiple modules

**Solution:**

```java
// ✅ CORRECT: Module-specific table names
// ordering-domain-module
@Entity
@Table(name = "ordering_plans")
public class OrderPlan { /* ... */ }

// invoicing-domain-module
@Entity
@Table(name = "invoicing_plans")
public class InvoicePlan { /* ... */ }
```

---

## Rules for AI Agents

- ✅ **DO**: Give each module its own named datasource
- ✅ **DO**: Make modules own their Flyway migrations
- ✅ **DO**: Use gateway interfaces or APIs for cross-module data needs
- ✅ **DO**: Prefix table names with module name
- ❌ **DON'T**: Share database tables between modules
- ❌ **DON'T**: Access other modules' data directly
- ❌ **DON'T**: Create duplicate entities with same `@Table` names
- ❌ **DON'T**: Use foreign keys across module boundaries

---

## Entity Naming Conventions

**Rule**: Table names MUST be prefixed with module name or use module-specific terminology.

### Good Patterns

```java
// Ordering module
@Table(name = "ordering_orders")
@Table(name = "ordering_order_items")
@Table(name = "ordering_customers")

// Inventory module
@Table(name = "inventory_products")
@Table(name = "inventory_stock_levels")
@Table(name = "inventory_warehouses")

// Invoicing module
@Table(name = "invoicing_invoices")
@Table(name = "invoicing_line_items")
@Table(name = "invoicing_payments")
```

### Bad Patterns

```java
// ❌ Generic names without module prefix
@Table(name = "plans")       // Which module owns this?
@Table(name = "users")       // Ordering or Invoicing?
@Table(name = "items")       // Too generic
@Table(name = "products")    // Ambiguous if multiple modules have products
```

---

## Code Examples

### ✅ GOOD: Module-Specific Datasource Configuration

```properties
# application.properties — each module gets its own named datasource

# Ordering module datasource
quarkus.datasource.ordering.db-kind=postgresql
quarkus.datasource.ordering.jdbc.url=${ORDERING_DB_URL:jdbc:postgresql://localhost:5432/ordering}
quarkus.datasource.ordering.username=${ORDERING_DB_USER:dev}
quarkus.datasource.ordering.password=${ORDERING_DB_PASSWORD:dev}

# Inventory module datasource
quarkus.datasource.inventory.db-kind=postgresql
quarkus.datasource.inventory.jdbc.url=${INVENTORY_DB_URL:jdbc:postgresql://localhost:5432/inventory}
quarkus.datasource.inventory.username=${INVENTORY_DB_USER:dev}
quarkus.datasource.inventory.password=${INVENTORY_DB_PASSWORD:dev}

# Flyway per module
quarkus.flyway.ordering.migrate-at-start=true
quarkus.flyway.ordering.locations=classpath:db/ordering

quarkus.flyway.inventory.migrate-at-start=true
quarkus.flyway.inventory.locations=classpath:db/inventory
```

### ✅ GOOD: Cross-Module Data Access via Gateway Interface

```java
// domain-api port — ordering module needs customer info from identity
public interface CustomerGateway {
    Optional<CustomerInfo> findCustomer(String customerId);
}

public record CustomerInfo(
    String customerId,
    String name,
    String email
) {}

// Infrastructure adapter — implements the port
@ApplicationScoped
public class CustomerGatewayAdapter implements CustomerGateway {

    @Inject
    @RestClient
    CustomerRestClient restClient;

    @Override
    public Optional<CustomerInfo> findCustomer(String customerId) {
        try {
            return Optional.of(restClient.getCustomer(customerId));
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
```

### ✅ GOOD: Using String References Instead of Foreign Keys

```java
@Entity
@Table(name = "ordering_orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // String reference to customer in another module — NOT a FK relationship
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    // Replicated data (denormalized) — only what ordering needs
    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    // NO @ManyToOne to CustomerEntity — that's in another module!
}
```

### ❌ BAD: Accessing Another Module's Repository

```java
@ApplicationScoped
public class OrderService {

    @Inject
    CustomerRepository customerRepository; // ❌ Wrong! This is from identity module

    public void placeOrder(String customerId) {
        var customer = customerRepository.findById(customerId); // ❌ Cross-module access
    }
}
```

---

## Detection Commands

**See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for all detection commands.**

**Most critical command for State Isolation**:

```bash
# Check for duplicate table names (CRITICAL - run first)
grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d
```

**Expected output**: Empty (no duplicates). If ANY duplicates are found, State Isolation is VIOLATED.

---

## State Isolation Verification Checklist

**MANDATORY**: Every AI agent MUST run this checklist before claiming State Isolation compliance:

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

**FAILURE CRITERIA**: If this finds ANY duplicates, State Isolation is VIOLATED regardless of other checks.

### Step 2: Cross-Module Repository Import Detection

```bash
# Check for repository imports from other modules
grep -rn 'import.*\.infra\.persistence\.' --include='*.java' . | grep -v '/test/' | \
  awk -F: '{print $1 " imports " $2}' | grep -v 'same-module'
```

### Step 3: Named Datasource Verification

```bash
# Verify each module has its own named datasource
grep -n 'quarkus.datasource\.' src/main/resources/application.properties
```

---

## Migration Strategies

### Scenario 1: Found Duplicate Table Names

**Problem**: Two modules using `@Table(name = "plans")`

**Solution Steps**:

1. **Identify ownership**: Determine which module truly owns this data
2. **Rename tables**: Prefix with module name
3. **Update Flyway migrations**: Create migration to rename tables
4. **Update entity references**: Fix all `@Table` annotations
5. **Verify**: Run detection commands again

**Example Flyway Migration**:

```sql
-- V2__rename_plan_tables.sql

-- Ordering module owns plans for orders
ALTER TABLE plans RENAME TO ordering_plans;

-- Invoicing module needs its own
CREATE TABLE invoicing_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    -- Copy structure, not data
    created_at TIMESTAMP DEFAULT now()
);

-- Migrate data if needed
INSERT INTO invoicing_plans (id, name, created_at)
SELECT id, name, created_at FROM ordering_plans WHERE type = 'invoicing';
```

### Scenario 2: Cross-Module Database Access

**Problem**: Service accessing another module's repository directly

**Solution Steps**:

1. **Create gateway port**: Define interface in `domain-api`
2. **Implement adapter**: Create infrastructure adapter
3. **Add REST client** (or CDI bridge): For consuming module
4. **Replace direct access**: Use gateway instead of repository
5. **Remove repository injection**: Clean up dependencies

**Example Refactor**:

```java
// BEFORE: Direct repository access
@ApplicationScoped
public class OrderService {
    @Inject
    CustomerRepository customerRepository; // ❌ Cross-module access

    public void processOrder(String customerId) {
        var customer = customerRepository.findById(customerId);
    }
}

// AFTER: Gateway-based access
@ApplicationScoped
public class OrderService {
    @Inject
    CustomerGateway customerGateway; // ✅ Uses port interface

    public void processOrder(String customerId) {
        var customer = customerGateway.findCustomer(customerId)
            .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
```

---

## Common Questions

### Q: Can modules share a database server?

**A**: Yes, but each module must have its own database/schema. Never share tables.

```properties
# ✅ GOOD: Same server, different databases
quarkus.datasource.ordering.jdbc.url=jdbc:postgresql://db.prod:5432/ordering_db
quarkus.datasource.inventory.jdbc.url=jdbc:postgresql://db.prod:5432/inventory_db
```

### Q: How do I handle user data across modules?

**A**: Use data replication. Each module stores only the user data it needs.

```java
// Identity module owns full user data
@Entity
@Table(name = "identity_users")
public class UserEntity {
    @Id private String id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    // ... all user fields
}

// Ordering module replicates minimal user data
@Entity
@Table(name = "ordering_orders")
public class OrderEntity {
    @Id private String id;
    private String customerId;    // String reference, not FK
    private String customerEmail; // Replicated for order emails
    private String customerName;  // Replicated for invoices
    // NO sensitive data like passwordHash
}
```

### Q: What about shared lookup tables?

**A**: Either:

1. Duplicate in each module (if small and stable)
2. Create a shared reference data service
3. Use events to sync reference data

**Never** share database tables directly.

---

## Automated Detection

### Pre-commit Hook Script

```bash
#!/bin/bash
# .git/hooks/pre-commit
echo "🔍 Checking State Isolation..."

VIOLATIONS=$(grep -rn '@Table' --include='*.java' . | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d)

if [ ! -z "$VIOLATIONS" ]; then
  echo "❌ COMMIT BLOCKED: Duplicate table names found:"
  echo "$VIOLATIONS"
  echo ""
  echo "Fix by using module-specific table names:"
  echo '  @Table(name = "module_entity")'
  exit 1
fi

echo "✅ State Isolation check passed"
```

---

## Summary

### ✅ State Isolation Checklist

- [ ] Each module has its own named datasource in `application.properties`
- [ ] All entities use module-prefixed table names (e.g., `ordering_orders`, not `orders`)
- [ ] No duplicate `@Table` names across modules
- [ ] Cross-module data access uses gateway interfaces, not direct repository access
- [ ] Each module owns its Flyway migrations independently
- [ ] No foreign key relationships across module boundaries
- [ ] Detection commands run clean (no violations)

### 🚫 Critical Violations to Avoid

1. ❌ Duplicate table names across modules
2. ❌ Cross-module repository injection
3. ❌ Shared database tables
4. ❌ Foreign keys to other modules' tables
5. ❌ Accessing another module's datasource

---

## Next Steps

- **Coding Patterns**: See [CODING-PATTERNS.md](./CODING-PATTERNS.md) for repository and service patterns
- **Implementation Checklist**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) for verification steps
- **Module Principles**: See [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) for principles 1-7

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
