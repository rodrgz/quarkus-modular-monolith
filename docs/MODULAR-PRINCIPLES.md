# Modular Architecture Principles (1-7)

This document provides detailed guidelines for the first 7 principles of modular architecture, focusing on module boundaries, interactions, and composition.

> **Navigation**: Return to [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | See also [STATE-ISOLATION.md](./STATE-ISOLATION.md) (Principle 8) | [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) (Principles 9-10)

## Quick Reference (For LLMs)

**When to use this doc**: Creating new modules, designing module boundaries, or implementing inter-module communication

**Key rules**:

- ✅ DO: Keep clear boundaries — export only `domain-api` interfaces
- ✅ DO: Use explicit Java interfaces (ports) for inter-module communication
- ✅ DO: Design modules to be composable and independent
- ❌ DON'T: Import `domain-internal` classes from other modules
- ❌ DON'T: Create tight coupling between modules

**Detection**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for detection commands

**See also**:

- [STATE-ISOLATION.md](./STATE-ISOLATION.md) - Principle 8 (database isolation)
- [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) - Principles 9-10
- [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) - Verification steps

## When to Read This Document

**Read this document when:**

- [ ] Creating a new domain module
- [ ] Designing module boundaries
- [ ] Implementing inter-module communication
- [ ] Understanding composability and independence
- [ ] Planning module deployment strategies

**Skip this document if:**

- You're only working with databases/entities (see [STATE-ISOLATION.md](./STATE-ISOLATION.md))
- You're only implementing services/resources (see [CODING-PATTERNS.md](./CODING-PATTERNS.md))
- You're only adding observability (see [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md))

## Table of Contents

- [1. Well-Defined Boundaries](#1-well-defined-boundaries)
- [2. Composability](#2-composability)
- [3. Independence](#3-independence)
- [4. Individual Scale](#4-individual-scale)
- [5. Explicit Communication](#5-explicit-communication)
- [6. Replaceability](#6-replaceability)
- [7. Deployment Independence](#7-deployment-independence)

---

## 1. Well-Defined Boundaries

**Definition**: Each module has clear responsibilities and doesn't expose internal details to other modules.

**Rules for AI Agents**:

- ✅ **DO**: Keep all domain logic within the module's boundaries
- ✅ **DO**: Export only `domain-api` interfaces (ports)
- ❌ **DON'T**: Import `domain-internal` classes from other modules
- ❌ **DON'T**: Share JPA entities between modules

**Code Examples**:

```java
// ✅ GOOD: Public port in domain-api (exported)
// ordering-domain-module/domain-api/src/main/java/org/acme/ordering/domain/api/OrderService.java
public interface OrderService {
    OrderId placeOrder(PlaceOrderCommand command);
    Optional<OrderSummary> findOrder(OrderId orderId);
}

// ❌ BAD: Exposing internal implementation
// Importing from another module's domain-internal
import org.acme.ordering.domain.internal.OrderServiceImpl;       // ❌ internal
import org.acme.ordering.infra.persistence.OrderEntity;          // ❌ infrastructure
import org.acme.ordering.infra.persistence.OrderRepository;      // ❌ infrastructure
```

---

## 2. Composability

**Definition**: Modules are designed as building blocks that can be combined flexibly to create different applications.

**Rules for AI Agents**:

- ✅ **DO**: Design modules to work independently or together
- ✅ **DO**: Create multiple services with different module combinations
- ✅ **DO**: Use CDI for loose coupling
- ❌ **DON'T**: Create tight coupling between modules

**Code Examples**:

```java
// ✅ GOOD: Composable service structure via Maven modules
// commerce-service/pom.xml — composes ordering + inventory modules
<dependencies>
    <dependency>
        <groupId>org.acme</groupId>
        <artifactId>ordering-domain-module</artifactId>
    </dependency>
    <dependency>
        <groupId>org.acme</groupId>
        <artifactId>inventory-domain-module</artifactId>
    </dependency>
</dependencies>

// invoicing-service/pom.xml — only what this service needs
<dependencies>
    <dependency>
        <groupId>org.acme</groupId>
        <artifactId>invoicing-domain-module</artifactId>
    </dependency>
</dependencies>
```

```java
// ✅ GOOD: Module with optional dependencies via CDI
@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    @Inject
    OrderRepository orderRepository;

    @Inject
    Instance<InventoryGateway> inventoryGateway; // Optional — may not be present

    public OrderId placeOrder(PlaceOrderCommand command) {
        // Use inventory check only if available
        if (inventoryGateway.isResolvable()) {
            inventoryGateway.get().reserveStock(command.productId(), command.quantity());
        }
        return orderRepository.save(Order.create(command));
    }
}
```

---

## 3. Independence

**Definition**: Modules operate autonomously without tight coupling in code or infrastructure.

**Rules for AI Agents**:

- ✅ **DO**: Ensure modules can be built, tested, and deployed independently
- ✅ **DO**: Use interfaces and events for inter-module communication
- ✅ **DO**: Make each module's tests runnable in isolation
- ❌ **DON'T**: Create shared mutable state between modules
- ❌ **DON'T**: Use direct class dependencies between modules

**Code Examples**:

```java
// ✅ GOOD: Independent module with its own configuration
// ordering-domain-module has its own domain, persistence, and tests

// ✅ GOOD: Communication via interfaces (not direct class calls)
// domain-api (public port)
public interface InventoryGateway {
    boolean isInStock(String productId, int quantity);
    void reserveStock(String productId, int quantity);
}

// infrastructure-module wires the adapter
@ApplicationScoped
public class InventoryGatewayAdapter implements InventoryGateway {
    @Inject
    InventoryService inventoryService; // from inventory-domain-module's domain-api

    @Override
    public boolean isInStock(String productId, int quantity) {
        return inventoryService.checkAvailability(productId, quantity);
    }
}

// ❌ BAD: Direct internal dependency on another module
@ApplicationScoped
public class OrderServiceImpl {
    @Inject
    InventoryServiceImpl inventoryService; // ❌ Direct coupling to internal class
}
```

**Testing Independence Example**:

```java
// ✅ GOOD: Each module has independent test setup
@QuarkusTest
@TestProfile(OrderingTestProfile.class) // Module-specific profile
class OrderServiceTest {

    @Inject
    OrderService orderService;

    @InjectMock
    InventoryGateway inventoryGateway; // Mock cross-module dependency

    @Test
    void shouldPlaceOrderIndependently() {
        when(inventoryGateway.isInStock("SKU-1", 2)).thenReturn(true);

        OrderId orderId = orderService.placeOrder(
            new PlaceOrderCommand("SKU-1", 2, "customer-1")
        );
        assertNotNull(orderId);
    }
}
```

---

## 4. Individual Scale

**Definition**: Each module can scale based on its specific resource needs without affecting others.

**Rules for AI Agents**:

- ✅ **DO**: Design modules to scale independently via multiple service instances
- ✅ **DO**: Use resource-specific configurations per module
- ✅ **DO**: Consider caching and performance optimizations per module
- ❌ **DON'T**: Create shared resource bottlenecks between modules

**Code Examples**:

```properties
# ✅ GOOD: Module-specific performance configuration
# application.properties — each module gets its own datasource pool
quarkus.datasource.ordering.db-kind=postgresql
quarkus.datasource.ordering.jdbc.max-size=20
quarkus.datasource.ordering.jdbc.min-size=5

quarkus.datasource.inventory.db-kind=postgresql
quarkus.datasource.inventory.jdbc.max-size=10
quarkus.datasource.inventory.jdbc.min-size=2
```

```yaml
# ✅ GOOD: Separate scaling in Kubernetes deployment
# k8s/commerce-service.yaml
apiVersion: apps/v1
kind: Deployment
spec:
  replicas: 3  # Scale based on commerce load
  template:
    spec:
      containers:
        - name: commerce-service
          resources:
            limits:
              memory: "512Mi"
---
# k8s/invoicing-service.yaml
spec:
  replicas: 1  # Lower traffic, fewer replicas
```

---

## 5. Explicit Communication

**Definition**: All inter-module communication happens through well-defined contracts.

**Rules for AI Agents**:

- ✅ **DO**: Define clear Java interfaces for all module interactions
- ✅ **DO**: Use value objects / records for data transfer between modules
- ✅ **DO**: Document all communication contracts
- ❌ **DON'T**: Access other modules' internal data structures
- ❌ **DON'T**: Make assumptions about other modules' implementations

**Code Examples**:

```java
// ✅ GOOD: Explicit interface contract in domain-api
// shared-domain-module or ordering-domain-module/domain-api
public interface InvoicingGateway {
    void issueInvoice(InvoiceRequest request);
    Optional<InvoiceStatus> getInvoiceStatus(String invoiceId);
}

// Value objects for data transfer (records, not entities)
public record InvoiceRequest(
    String orderId,
    String customerId,
    BigDecimal totalAmount,
    List<LineItem> lineItems
) {}

public record InvoiceStatus(
    String invoiceId,
    Status status,
    Instant issuedAt
) {
    public enum Status { PENDING, ISSUED, PAID, OVERDUE }
}
```

```java
// ✅ GOOD: Implementation respects the contract
@ApplicationScoped
public class InvoicingGatewayAdapter implements InvoicingGateway {

    @Inject
    InvoicingService invoicingService;

    @Override
    public void issueInvoice(InvoiceRequest request) {
        invoicingService.createInvoice(
            request.orderId(), request.customerId(), request.totalAmount()
        );
    }
}

// ✅ GOOD: Consumer uses only the interface
@ApplicationScoped
public class OrderCompletionService {

    @Inject
    InvoicingGateway invoicingGateway; // Interface only, not implementation

    public void completeOrder(Order order) {
        invoicingGateway.issueInvoice(new InvoiceRequest(
            order.id().value(), order.customerId(), order.totalAmount(), List.of()
        ));
    }
}
```

```java
// ✅ GOOD: Event-based communication with explicit payload
public record OrderPlacedEvent(
    String orderId,
    String customerId,
    BigDecimal totalAmount,
    Instant placedAt
) {}

// Publisher
@ApplicationScoped
public class OrderEventPublisher {

    @Inject
    @Channel("order-events")
    Emitter<OrderPlacedEvent> emitter;

    public void publishOrderPlaced(Order order) {
        emitter.send(new OrderPlacedEvent(
            order.id().value(), order.customerId(),
            order.totalAmount(), Instant.now()
        ));
    }
}

// Subscriber (in another module)
@ApplicationScoped
public class InvoicingEventConsumer {

    @Incoming("order-events")
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Process event — type-safe handling
    }
}
```

---

## 6. Replaceability

**Definition**: Modules can be substituted without affecting other parts of the architecture.

**Rules for AI Agents**:

- ✅ **DO**: Design modules to be swappable behind interfaces (ports)
- ✅ **DO**: Avoid exposing implementation details
- ✅ **DO**: Use CDI for all module dependencies
- ❌ **DON'T**: Create hard dependencies on specific implementations
- ❌ **DON'T**: Export concrete classes as module APIs

**Code Examples**:

```java
// ✅ GOOD: Replaceable payment service design
// domain-api — port interface
public interface PaymentGateway {
    PaymentResult processPayment(PaymentRequest request);
    RefundResult refundPayment(String paymentId);
}

// ✅ GOOD: Multiple implementations of the same interface
@ApplicationScoped
@Named("stripe")
public class StripePaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Stripe-specific implementation
    }
}

@ApplicationScoped
@Named("paypal")
public class PayPalPaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // PayPal-specific implementation
    }
}
```

```java
// ✅ GOOD: Configurable implementation selection via CDI producer
@ApplicationScoped
public class PaymentGatewayProducer {

    @ConfigProperty(name = "payment.provider", defaultValue = "stripe")
    String provider;

    @Inject @Named("stripe")
    PaymentGateway stripeGateway;

    @Inject @Named("paypal")
    PaymentGateway paypalGateway;

    @Produces
    @ApplicationScoped
    public PaymentGateway paymentGateway() {
        return switch (provider) {
            case "stripe" -> stripeGateway;
            case "paypal" -> paypalGateway;
            default -> throw new IllegalStateException("Unknown provider: " + provider);
        };
    }
}

// ✅ GOOD: Consumer doesn't know about specific implementation
@ApplicationScoped
public class ProcessPaymentUseCase {

    @Inject
    PaymentGateway paymentGateway; // Interface only — implementation injected by CDI

    public PaymentResult execute(PaymentRequest request) {
        return paymentGateway.processPayment(request);
    }
}
```

---

## 7. Deployment Independence

**Definition**: Modules don't dictate how they're deployed — they can run as monolith or distributed services.

**Rules for AI Agents**:

- ✅ **DO**: Design modules to work in any deployment configuration
- ✅ **DO**: Use MicroProfile Config / `@ConfigProperty` for deployment-specific config
- ✅ **DO**: Keep deployment logic in service modules, not domain modules
- ❌ **DON'T**: Hard-code deployment assumptions in modules
- ❌ **DON'T**: Make modules aware of their deployment context

**Code Examples**:

```properties
# ✅ GOOD: Module is deployment-agnostic — config drives behavior
# application.properties
quarkus.datasource.ordering.db-kind=postgresql
quarkus.datasource.ordering.jdbc.url=${ORDERING_DB_URL:jdbc:postgresql://localhost:5432/ordering}
quarkus.datasource.ordering.username=${ORDERING_DB_USER:dev}
quarkus.datasource.ordering.password=${ORDERING_DB_PASSWORD:dev}

# Module doesn't care if this is local, RDS, or containerized
```

```xml
<!-- ✅ GOOD: Multiple deployment strategies for same modules -->

<!-- commerce-service/pom.xml (Monolith — everything together) -->
<dependencies>
    <dependency><artifactId>ordering-domain-module</artifactId></dependency>
    <dependency><artifactId>inventory-domain-module</artifactId></dependency>
    <dependency><artifactId>invoicing-domain-module</artifactId></dependency>
</dependencies>

<!-- ordering-microservice/pom.xml (Microservice — same module, different deployment) -->
<dependencies>
    <dependency><artifactId>ordering-domain-module</artifactId></dependency>
    <!-- Only what this service needs -->
</dependencies>
```

```properties
# ✅ GOOD: Environment-specific configurations
# commerce-service — production profile
%prod.quarkus.datasource.ordering.jdbc.url=jdbc:postgresql://ordering-db.prod:5432/ordering
%prod.quarkus.datasource.inventory.jdbc.url=jdbc:postgresql://inventory-db.prod:5432/inventory

# ordering-microservice — different deployment, same module
%prod.quarkus.datasource.ordering.jdbc.url=jdbc:postgresql://ordering-db.prod:5432/ordering
```

---

## Summary: Principles 1-7 Checklist

When designing or reviewing modules, ensure:

- [ ] **Boundaries** are well-defined with clear `domain-api` exports
- [ ] **Composition** is possible — module works alone or with others via Maven
- [ ] **Independence** is maintained — no shared mutable state
- [ ] **Scaling** can happen per-module with specific Quarkus configs
- [ ] **Communication** uses explicit Java interfaces and value objects
- [ ] **Replacement** is possible via CDI injection and port interfaces
- [ ] **Deployment** flexibility via MicroProfile Config and environment variables

## Next Steps

- **Principle 8 (State Isolation)**: See [STATE-ISOLATION.md](./STATE-ISOLATION.md)
- **Principles 9-10 (Observability & Resilience)**: See [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md)
- **Implementation Patterns**: See [CODING-PATTERNS.md](./CODING-PATTERNS.md)
- **Verification Checklist**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md)

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
