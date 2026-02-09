# Detection Commands Reference

Complete reference for all detection commands used in modularity maturity assessment for Java/Quarkus modular monoliths.

## State Isolation Detection (Principle 8)

### Duplicate Table Names (CRITICAL)

**Command**:
```bash
grep -rn "@Table" --include="*.java" | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d
```

**What it detects**: JPA entities with duplicate table names across modules

**Why it matters**: Duplicate table names cause Hibernate conflicts and violate state isolation

**Example violation**:
```
billing/src/.../SubscriptionEntity.java:  @Table(name = "subscription")
content/src/.../SubscriptionEntity.java:  @Table(name = "subscription")
```

**How to fix**: Prefix table names with module identifier
```java
// billing module
@Table(name = "billing_subscription")

// content module
@Table(name = "content_subscription")
```

---

### Cross-Module Entity Imports

**Command**:
```bash
grep -rn "import.*\.infra\.persistence\." --include="*.java" | grep -v "/test/"
```

**What it detects**: Modules importing persistence classes from other modules

**Why it matters**: Violates state isolation and creates tight coupling

**Example violation**:
```java
// In commerce module
import com.example.identity.infra.persistence.UserEntity; // ❌
```

**How to fix**: Use domain interfaces (ports) and DTOs
```java
// Define a port in domain/api
public interface UserGateway {
    UserDTO findById(String userId);
}
```

---

### CDI Annotations in Domain (CRITICAL)

**Command**:
```bash
grep -rn "@ApplicationScoped\|@Inject\|@Produces\|@Singleton\|@RequestScoped" --include="*.java" $(find . -path "*/domain/*" -name "*.java")
```

**What it detects**: Framework annotations in domain layer

**Why it matters**: Domain must be pure Java — no framework dependencies

**Example violation**:
```java
// ❌ BAD: CDI in domain
@ApplicationScoped
public class OrderCalculatorImpl implements OrderCalculator {
    @Inject InventoryGateway gateway;
}
```

**How to fix**: Use constructor injection and factory pattern
```java
// ✅ GOOD: Pure domain
public class OrderCalculatorImpl implements OrderCalculator {
    private final InventoryGateway gateway;
    public OrderCalculatorImpl(InventoryGateway gateway) {
        this.gateway = gateway;
    }
}
```

---

## Resource Pattern Detection

### Repository Injection in Resources (VIOLATION)

**Command**:
```bash
grep -rn "Repository" --include="*Resource.java"
```

**What it detects**: Resources directly referencing repositories

**Why it matters**: Resources should only call services/use cases, not data layer

**Example violation**:
```java
// ❌ BAD
@Path("/orders")
public class OrderResource {
    @Inject OrderRepository repository; // ❌
}
```

**How to fix**: Inject services or use cases
```java
// ✅ GOOD
@Path("/orders")
public class OrderResource {
    @Inject ProcessOrderUseCase useCase; // ✅
}
```

---

### Fat Resources (>100 lines)

**Command**:
```bash
find . -name "*Resource.java" -not -path "*/test/*" -exec wc -l {} \; | awk '$1 > 100'
```

**What it detects**: Resources with excessive lines of code

**Why it matters**: Indicates business logic in resources instead of services

**How to fix**: Move logic to services/use cases, keep resources thin
```java
// ✅ GOOD: Thin resource
@POST
public Response create(CreateOrderRequest request) {
    var result = useCase.execute(request);
    return Response.ok(result).build();
}
```

---

## Transaction Management Detection

### Write Operations Without @Transactional

**Command**:
```bash
grep -rn "public.*\(create\|update\|delete\|save\|remove\|persist\)" --include="*.java" $(find . -path "*/infra/*" -name "*ServiceImpl.java" -o -name "*Adapter.java") | grep -v "@Transactional" | grep -v "test"
```

**What it detects**: Infrastructure write operations without transaction management

**Why it matters**: Data consistency issues and potential corruption

**Example violation**:
```java
// ❌ BAD: No transaction
public void createOrder(OrderData data) {
    orderRepository.persist(new OrderEntity(data));
    inventoryRepository.update(data.productId());
}
```

**How to fix**: Add `@Transactional`
```java
// ✅ GOOD
@Transactional
public void createOrder(OrderData data) {
    orderRepository.persist(new OrderEntity(data));
    inventoryRepository.update(data.productId());
}
```

---

## Repository Pattern Detection

### Direct JPA Usage in Services

**Command**:
```bash
grep -rn "import jakarta.persistence" --include="*.java" $(find . -path "*/domain/*" -name "*.java")
```

**What it detects**: Domain classes importing JPA

**Why it matters**: Domain should be framework-agnostic; persistence belongs in infrastructure

**How to fix**: Use port interfaces in domain, implement with JPA in infrastructure

---

### Services Without CDI Scope (Infrastructure)

**Command**:
```bash
grep -rn "class.*ServiceImpl\|class.*Adapter" --include="*.java" $(find . -path "*/infra/*" -name "*.java") | while read line; do
    file=$(echo "$line" | cut -d: -f1)
    grep -L "@ApplicationScoped\|@Singleton" "$file"
done
```

**What it detects**: Infrastructure services without CDI scope

**Why it matters**: Infrastructure services need CDI scope for dependency injection

---

## Observability Detection (Principle 9)

### Missing Health Checks

**Command**:
```bash
grep -rn "@Liveness\|@Readiness" --include="*.java" -l
```

**What it detects**: Presence of health check implementations

**Why it matters**: Required for production readiness and Kubernetes probes

**How to fix**:
```java
@Liveness
@ApplicationScoped
public class ServiceLivenessCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("service-live");
    }
}
```

---

### Missing Logging

**Command**:
```bash
grep -rn "class.*ServiceImpl\|class.*Adapter\|class.*Resource" --include="*.java" -l | while read file; do
    grep -L "Logger\|Log " "$file"
done
```

**What it detects**: Service/adapter/resource classes without logger

**Why it matters**: Cannot observe service behavior without logging

**How to fix**:
```java
import org.jboss.logging.Logger;

public class OrderServiceImpl {
    private static final Logger LOG = Logger.getLogger(OrderServiceImpl.class);
}
// Or with Lombok: @Slf4j
```

---

## Boundary Detection (Principle 1)

### Internal Package Access

**Command**:
```bash
grep -rn "import.*\.internal\." --include="*.java" | grep -v "Factory\|Test\|internal/"
```

**What it detects**: Classes importing from `internal` packages of other modules

**Why it matters**: Internal implementations should not be accessed from outside

**Example violation**:
```java
// ❌ BAD: Importing internal implementation
import com.example.commerce.domain.internal.OrderCalculatorImpl;
```

**How to fix**: Use the public API (interfaces from `domain/api/`)
```java
// ✅ GOOD: Use interface
import com.example.commerce.domain.api.OrderCalculator;
```

---

### ArchUnit Rule Verification

**Command**:
```bash
grep -rn "@ArchTest" --include="*.java" -l
```

**What it detects**: Presence of ArchUnit architectural rules

**Why it matters**: Automated enforcement of architectural boundaries

**Expected**: Each service module should have an `ArchitectureTest.java` with rules for:
- Domain isolation from infrastructure
- Resource → Service → Repository layering
- No circular dependencies
- Internal package encapsulation

---

## Resilience Detection (Principle 10)

### Missing Fault Tolerance on External Calls

**Command**:
```bash
grep -rn "class.*Adapter\|class.*Client" --include="*.java" -l | while read file; do
    grep -L "@Retry\|@CircuitBreaker\|@Timeout\|@Fallback" "$file"
done
```

**What it detects**: External API adapters without fault tolerance annotations

**Why it matters**: External failures cascade without protection

**How to fix**:
```java
@ApplicationScoped
public class PaymentAdapter implements PaymentGateway {

    @Retry(maxRetries = 3)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "fallbackCharge")
    public PaymentResult charge(PaymentRequest request) {
        return restClient.charge(request);
    }

    public PaymentResult fallbackCharge(PaymentRequest request) {
        return PaymentResult.deferred("Service unavailable, queued for retry");
    }
}
```

---

## Running All Detection Commands

```bash
#!/bin/bash
# modularity-detection.sh

echo "=== State Isolation ==="
echo "Duplicate Tables:"
grep -rn "@Table" --include="*.java" | grep -oP 'name\s*=\s*"[^"]*"' | sort | uniq -d

echo -e "\nCDI in Domain:"
grep -rn "@ApplicationScoped\|@Inject" --include="*.java" $(find . -path "*/domain/*" -name "*.java") 2>/dev/null

echo -e "\n=== Resource Patterns ==="
echo "Repository Injections:"
grep -rn "Repository" --include="*Resource.java"

echo -e "\n=== Transactions ==="
echo "Write Ops Without @Transactional (infra layer):"
grep -B2 "public.*create\|public.*update\|public.*delete\|public.*save\|public.*persist" --include="*Adapter.java" -rn | grep -v "@Transactional"

echo -e "\n=== Boundaries ==="
echo "Internal Package Access:"
grep -rn "import.*\.internal\." --include="*.java" | grep -v "Factory\|Test\|internal/"

echo -e "\n=== ArchUnit ==="
echo "ArchUnit Test Files:"
grep -rn "@ArchTest" --include="*.java" -l

echo -e "\n=== Health Checks ==="
grep -rn "@Liveness\|@Readiness" --include="*.java" -l
```

**Usage**:
```bash
chmod +x modularity-detection.sh
./modularity-detection.sh > assessment-results.txt
```

---

## Severity Levels

**Critical (P0)**:
- Duplicate table names
- CDI annotations in domain
- Cross-module persistence access
- Missing transactions on writes

**High (P1)**:
- Repository injection in resources
- Fat resources (>100 lines)
- Missing error handling
- No fault tolerance on external APIs

**Medium (P2)**:
- Missing logger injection
- Internal package access from outside
- Missing health checks
- Missing ArchUnit rules

**Low (P3)**:
- Code organization improvements
- Documentation gaps
- Optimization opportunities

## False Positives

Some patterns may be detected but are acceptable:

1. **Test files**: Detection commands may flag test setup code
2. **Configuration classes**: CDI `@Produces` in `infra/config/` is expected
3. **Migration files**: Database migrations may have direct SQL
4. **Factory classes**: Factories in `domain/api/` may import `domain/internal/` (by design)

Always verify context before marking as violation.

---

**Last Updated**: February 2026
