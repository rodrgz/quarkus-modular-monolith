# Coding Patterns for Modular Architecture

This document provides technical implementation patterns for repositories, JAX-RS resources, and transaction management in our modular architecture.

> **Navigation**: Return to [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | See also [STATE-ISOLATION.md](./STATE-ISOLATION.md) | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md)

## Quick Reference (For LLMs)

**When to use this doc**: Implementing repositories, JAX-RS resources, services, or transaction management

**Key rules**:

- ✅ DO: Extend `PanacheRepositoryBase<Entity, Id>` for repositories
- ✅ DO: Keep JAX-RS resources lean (<20 lines), only call services
- ✅ DO: Use `@Transactional` on service methods that perform write operations
- ❌ DON'T: Inject `EntityManager` directly in services
- ❌ DON'T: Put business logic in JAX-RS resources or call repositories from resources

**Detection**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for detection commands

**See also**:

- [STATE-ISOLATION.md](./STATE-ISOLATION.md) - Entity naming and database patterns
- [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) - Verification steps

## When to Read This Document

**Read this document when:**

- [ ] Creating or modifying Panache repositories
- [ ] Creating or modifying JAX-RS resources
- [ ] Implementing transaction management
- [ ] Refactoring fat resources to lean resources
- [ ] Setting up datasource connections in services

**Skip this document if:**

- You're only working with entities (see [STATE-ISOLATION.md](./STATE-ISOLATION.md))
- You're only integrating external APIs (see [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md))
- You're only adding logging/monitoring (see [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md))

## Table of Contents

- [Repository Pattern & Panache Encapsulation](#repository-pattern--panache-encapsulation)
- [JAX-RS Resource Responsibilities & Lean Pattern](#jax-rs-resource-responsibilities--lean-pattern)
- [Transaction Management](#transaction-management)

---

## Repository Pattern & Panache Encapsulation

### Definition

Repositories MUST encapsulate all persistence-specific logic and never expose internal JPA/Hibernate APIs directly to the domain layer.

### Rules for AI Agents

- ✅ **DO**: Implement `PanacheRepositoryBase<Entity, Id>` for all repositories
- ✅ **DO**: Mark repositories as `@ApplicationScoped`
- ✅ **DO**: Add custom query methods with business meaning
- ❌ **DON'T**: Inject `EntityManager` directly in services
- ❌ **DON'T**: Expose Hibernate criteria or raw query builders to services
- ❌ **DON'T**: Use `PanacheEntity` (active record) — use the repository pattern

### Why PanacheRepositoryBase?

Using `PanacheRepositoryBase` with the repository pattern provides:

- **Encapsulation**: Wraps Hibernate/JPA internals behind repository methods
- **Business-named queries**: Services call `findActiveByCustomerId()` not raw JPQL
- **Testability**: Easy to mock repositories in unit tests
- **Consistency**: Standard pattern across all modules
- **Separation of concerns**: Domain layer doesn't know about JPA

### Code Examples

```java
// ✅ GOOD: Proper Panache repository
// ordering-infrastructure-module/.../OrderRepository.java
@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<OrderEntity, String> {

    // Custom query methods with business meaning
    public List<OrderEntity> findByCustomerId(String customerId) {
        return find("customerId", Sort.descending("createdAt"), customerId).list();
    }

    public Optional<OrderEntity> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }

    public long countActiveOrders(String customerId) {
        return count("customerId = ?1 and status = ?2", customerId, OrderStatus.ACTIVE);
    }
}

// ❌ BAD: Injecting EntityManager directly in services
@ApplicationScoped
public class OrderService {
    @Inject
    EntityManager em; // ❌ Service coupled to JPA internals

    public List<Order> getOrders(String customerId) {
        // Service now coupled to JPQL — impossible to test without DB
        return em.createQuery(
            "SELECT o FROM OrderEntity o WHERE o.customerId = :cid", OrderEntity.class
        ).setParameter("cid", customerId).getResultList();
    }
}

// ✅ GOOD: Service uses repository abstraction
@ApplicationScoped
public class OrderService {
    @Inject
    OrderRepository orderRepository;

    public List<Order> getOrders(String customerId) {
        // Service only knows about business methods
        return orderRepository.findByCustomerId(customerId)
            .stream().map(this::toDomain).toList();
    }
}
```

### Testing Benefits

```java
// Easy to mock with simple interface
@InjectMock
OrderRepository orderRepository;

@Test
void shouldReturnOrdersByCustomer() {
    when(orderRepository.findByCustomerId("cust-1"))
        .thenReturn(List.of(testOrderEntity()));

    var orders = orderService.getOrders("cust-1");
    assertEquals(1, orders.size());
}
// No need to mock EntityManager, CriteriaBuilder, etc.
```

---

## JAX-RS Resource Responsibilities & Lean Pattern

### Definition

JAX-RS resources MUST be lean and only handle HTTP concerns (input/output). All business logic, orchestration, and data access MUST live in services.

### Rules for AI Agents

- ✅ **DO**: Keep resources under 20 lines per method
- ✅ **DO**: Only call services (never repositories)
- ✅ **DO**: Only handle: request validation, service calls, response mapping
- ✅ **DO**: Use Java records for DTOs
- ❌ **DON'T**: Put business logic in resources
- ❌ **DON'T**: Call repositories directly from resources
- ❌ **DON'T**: Perform calculations or data aggregation in resources
- ❌ **DON'T**: Handle entity relationships in resources

### Why Lean Resources?

**Fat resources lead to:**

- Untestable business logic (requires HTTP context)
- Duplicated logic across endpoints
- Tight coupling to JAX-RS framework
- Difficult to reuse logic (CLI, messaging, scheduled tasks)
- Poor separation of concerns

**Lean resources provide:**

- Framework-agnostic business logic
- Reusable services across contexts (HTTP, messaging, CLI)
- Easy unit testing of business logic
- Clear separation: HTTP ↔ Application ↔ Domain

### Resource Responsibilities (ONLY)

1. **Extract request data** (path params, body, query params, headers)
2. **Validate request** (via Bean Validation `@Valid`)
3. **Extract security context** (`@Context SecurityContext` or `JsonWebToken`)
4. **Call service method** (single call, pass primitives/DTOs)
5. **Transform response** (entity → DTO record)
6. **Handle HTTP errors** (translate domain exceptions to HTTP responses)

### Code Examples

```java
// ✅ GOOD: Lean JAX-RS resource (8 lines of logic)
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class OrderResource {

    @Inject
    OrderService orderService;

    @Inject
    JsonWebToken jwt;

    @GET
    public List<OrderResponse> getCustomerOrders() {
        String customerId = jwt.getSubject();
        return orderService.getOrdersByCustomer(customerId)
            .stream().map(OrderResponse::from).toList();
    }

    @POST
    public Response placeOrder(@Valid PlaceOrderRequest request) {
        String customerId = jwt.getSubject();
        OrderId orderId = orderService.placeOrder(customerId, request);
        return Response.status(Response.Status.CREATED)
            .entity(Map.of("orderId", orderId.value()))
            .build();
    }
}

// DTO records
public record OrderResponse(String orderId, String status, BigDecimal total) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id().value(), order.status().name(), order.total());
    }
}

public record PlaceOrderRequest(
    @NotBlank String productId,
    @Min(1) int quantity
) {}
```

```java
// ❌ BAD: Fat resource with business logic (50+ lines)
@Path("/usage")
public class UsageResource {

    @Inject
    UsageRecordRepository usageRepo;        // ❌ Repository injection
    @Inject
    SubscriptionRepository subscriptionRepo; // ❌ Repository injection

    @GET
    @Path("/subscription/{subscriptionId}")
    public Response getUsageSummary(@PathParam("subscriptionId") String subscriptionId) {
        // ❌ Direct repository call
        var subscription = subscriptionRepo.findById(subscriptionId);
        if (subscription == null) {
            return Response.status(404).build();
        }

        // ❌ Business logic in resource
        var records = usageRepo.findBySubscriptionAndPeriod(
            subscriptionId, subscription.getCurrentPeriodStart(), Instant.now()
        );

        // ❌ Data transformation and calculation logic
        var summaries = new ArrayList<Map<String, Object>>();
        for (var record : records) {
            var includedQuota = subscription.getPlan().getIncludedQuota(record.getType());
            var billableQty = Math.max(0, record.getQuantity() - includedQuota);
            var estimatedCost = billableQty * 0.10; // ❌ Business rule in resource!

            summaries.add(Map.of(
                "type", record.getType(),
                "total", record.getQuantity(),
                "billable", billableQty,
                "cost", estimatedCost
            ));
        }
        return Response.ok(summaries).build(); // 50+ lines of logic!
    }
}

// ✅ GOOD: Same logic moved to service
@ApplicationScoped
public class UsageBillingService {

    @Inject
    UsageRecordRepository usageRecordRepository;
    @Inject
    SubscriptionRepository subscriptionRepository;

    public List<UsageSummary> getUsageSummary(String subscriptionId) {
        var subscription = subscriptionRepository.findByIdOptional(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));

        var records = usageRecordRepository.findBySubscriptionAndPeriod(
            subscriptionId, subscription.getCurrentPeriodStart(), Instant.now()
        );

        return records.stream().map(record -> {
            var quota = subscription.getPlan().getIncludedQuota(record.getType());
            var billable = Math.max(0, record.getQuantity() - quota);
            return new UsageSummary(record.getType(), record.getQuantity(),
                                   billable, calculateCost(billable, record.getType()));
        }).toList();
    }

    private BigDecimal calculateCost(int quantity, UsageType type) {
        return BigDecimal.valueOf(quantity).multiply(getUnitPrice(type));
    }
}

// ✅ GOOD: Lean resource using service
@Path("/usage")
public class UsageResource {

    @Inject
    UsageBillingService usageBillingService;

    @GET
    @Path("/subscription/{subscriptionId}")
    public List<UsageSummaryResponse> getUsageSummary(
            @PathParam("subscriptionId") String subscriptionId) {
        return usageBillingService.getUsageSummary(subscriptionId)
            .stream().map(UsageSummaryResponse::from).toList();
    }
}
```

### Service vs Resource Responsibilities

| Responsibility                | Service | Resource |
| ----------------------------- | ------- | -------- |
| Business Logic                | ✅ YES  | ❌ NO    |
| Domain Validation             | ✅ YES  | ❌ NO    |
| Repository Calls              | ✅ YES  | ❌ NO    |
| Calculations                  | ✅ YES  | ❌ NO    |
| Orchestration                 | ✅ YES  | ❌ NO    |
| Entity Relationships          | ✅ YES  | ❌ NO    |
| Request Validation (Bean Val) | ❌ NO   | ✅ YES   |
| HTTP Status Codes             | ❌ NO   | ✅ YES   |
| Response Mapping (Entity→DTO) | ❌ NO   | ✅ YES   |
| Security Context Extraction   | ❌ NO   | ✅ YES   |

### Common Violations

#### 1. Direct Repository Calls

```java
// ❌ BAD
@Path("/subscription")
public class SubscriptionResource {
    @Inject SubscriptionRepository repo;

    @GET @Path("/{id}")
    public SubscriptionEntity get(@PathParam("id") String id) {
        return repo.findById(id);
    }
}

// ✅ GOOD
@Path("/subscription")
public class SubscriptionResource {
    @Inject SubscriptionService service;

    @GET @Path("/{id}")
    public SubscriptionResponse get(@PathParam("id") String id) {
        return SubscriptionResponse.from(service.getById(id));
    }
}
```

#### 2. Business Validation in Resource

```java
// ❌ BAD
@POST @Path("/{id}/change-plan")
public Response changePlan(@PathParam("id") String id, @Valid ChangePlanRequest dto) {
    var sub = subscriptionRepo.findById(id);
    if (sub == null) throw new NotFoundException();     // ❌ in resource
    if (!sub.getUserId().equals(userId)) throw new ForbiddenException(); // ❌ in resource

    return Response.ok(service.changePlan(sub.getId(), dto.newPlanId())).build();
}

// ✅ GOOD
@POST @Path("/{id}/change-plan")
public Response changePlan(@PathParam("id") String id, @Valid ChangePlanRequest dto) {
    String userId = jwt.getSubject();
    // Service handles all validation and business logic
    var result = service.changePlanForUser(userId, id, dto.newPlanId());
    return Response.ok(result).build();
}
```

### Testing Benefits

```java
// ✅ Service is easily testable without HTTP context
@QuarkusTest
class UsageBillingServiceTest {

    @Inject UsageBillingService service;

    @Test
    void shouldCalculateUsageSummary() {
        var summary = service.getUsageSummary("sub-123");
        assertEquals(25, summary.get(0).billableQuantity());
    }
}

// ❌ Fat resource requires full HTTP test setup just to test business logic
```

---

## Transaction Management

### Definition

Services that perform database write operations (create, update, delete) MUST use the `@Transactional` annotation to ensure data consistency.

### Rules for AI Agents

- ✅ **DO**: Use `@Transactional` on all service methods that perform write operations
- ✅ **DO**: Apply to methods that orchestrate multiple write operations
- ✅ **DO**: Apply to methods that must maintain data consistency
- ❌ **DON'T**: Add `@Transactional` to read-only methods
- ❌ **DON'T**: Add `@Transactional` to JAX-RS resources (put it on services)
- ❌ **DON'T**: Nest `@Transactional` methods (call from transactional to non-transactional only)

### When to Use @Transactional

**Always use for:**

1. **Single write operation** — Ensures atomicity
2. **Multiple write operations** — All-or-nothing semantics
3. **Read-then-write** — Prevents race conditions
4. **Cross-entity operations** — Maintains referential integrity

**Examples:**

```java
// ✅ Single write — ensures atomic save
@Transactional
public OrderId placeOrder(PlaceOrderCommand command) {
    var order = Order.create(command);
    orderRepository.persist(order.toEntity());
    return order.id();
}

// ✅ Multiple writes — all succeed or all fail
@Transactional
public void addOrderItem(String orderId, String productId, int quantity) {
    var order = orderRepository.findByIdOptional(orderId)
        .orElseThrow(() -> new NotFoundException("Order not found"));

    var item = new OrderItemEntity(orderId, productId, quantity, getUnitPrice(productId));
    orderItemRepository.persist(item);

    order.setTotalAmount(recalculateTotal(orderId));
    orderRepository.persist(order); // Both saves succeed or both rollback
}

// ✅ Complex orchestration — maintains consistency
@Transactional
public ChangePlanResult changePlan(String customerId, String newPlanId) {
    // 1. Calculate proration
    var proration = calculateProration(customerId);

    // 2. Create invoice
    var invoice = new InvoiceEntity(customerId, proration.amount());
    invoiceRepository.persist(invoice);

    // 3. Update subscription
    var subscription = subscriptionRepository.findByCustomerId(customerId);
    subscription.setPlanId(newPlanId);
    subscriptionRepository.persist(subscription);

    // 4. Apply credits
    var credit = new CreditEntity(customerId, proration.credit());
    creditRepository.persist(credit);

    return new ChangePlanResult(invoice, subscription); // All operations atomic
}

// ❌ Read-only — no transaction needed
public Optional<Order> getOrder(String orderId) {
    return orderRepository.findByIdOptional(orderId).map(this::toDomain);
}

// ❌ Multiple independent reads — no transaction needed
public DashboardData getDashboard(String customerId) {
    var orders = orderRepository.findByCustomerId(customerId);
    var invoices = invoiceRepository.findByCustomerId(customerId);
    return new DashboardData(orders, invoices);
}
```

### Anti-Patterns to Avoid

#### ❌ @Transactional on Resources

```java
// BAD: Transaction on resource — should be on service
@POST
@Transactional
public Response createOrder(@Valid CreateOrderRequest req) {
    // ...
}

// GOOD: Transaction on service
@ApplicationScoped
public class OrderService {
    @Transactional
    public OrderId createOrder(CreateOrderCommand command) { /* ... */ }
}
```

#### ❌ Nested Transactional Methods

```java
// BAD: Calling transactional from transactional
@Transactional
public void outerMethod() {
    innerMethod(); // innerMethod is also @Transactional
}

@Transactional
public void innerMethod() {
    // Nested transaction — problematic
}

// GOOD: Only outer method has annotation
@Transactional
public void outerMethod() {
    innerMethod(); // NOT annotated
}

void innerMethod() {
    // Participates in outer transaction
}
```

#### ❌ Transaction on Read-Only Methods

```java
// BAD: Unnecessary transaction overhead
@Transactional
public List<Order> getAllOrders() {
    return orderRepository.listAll();
}

// GOOD: No transaction for reads
public List<Order> getAllOrders() {
    return orderRepository.listAll();
}
```

### Detection Commands

**See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for all detection commands.**

**Quick checks for coding patterns**:

```bash
# Resources with repository injections (violation)
grep -rn 'Repository' --include='*.java' */infra/rest/ */infra/resource/

# Write operations without @Transactional
grep -rn '\.persist\|\.delete\|\.flush' --include='*.java' */domain/ | grep -L '@Transactional'
```

---

## Summary

### Implementation Checklist

When implementing features:

- [ ] **Repository**: Implement `PanacheRepositoryBase`, `@ApplicationScoped`, business-named methods
- [ ] **Resource**: Lean (<20 lines/method), only call services, Bean Validation, DTO records
- [ ] **Service**: `@Transactional` for writes, `@ApplicationScoped`, all business logic
- [ ] **Entity**: `@Table(name = "module_entity")`, module-prefixed table names

### Coding Patterns Quick Reference

| Layer        | Java/Quarkus Pattern              | Key Annotation              |
| ------------ | --------------------------------- | --------------------------- |
| Repository   | `PanacheRepositoryBase<E, ID>`    | `@ApplicationScoped`        |
| Service      | CDI bean                          | `@ApplicationScoped`        |
| Transaction  | Jakarta Transactions              | `@Transactional`            |
| Resource     | JAX-RS                            | `@Path`, `@GET`, `@POST`    |
| DTO          | Java records                      | Bean Validation (`@Valid`)   |
| Entity       | JPA                               | `@Entity`, `@Table`         |
| Config       | MicroProfile Config               | `@ConfigProperty`           |

---

## Next Steps

- **State Isolation**: See [STATE-ISOLATION.md](./STATE-ISOLATION.md) for entity and database patterns
- **Module Principles**: See [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) for architecture principles
- **Resilience**: See [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) for monitoring patterns
- **Verification**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) for checking compliance

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
