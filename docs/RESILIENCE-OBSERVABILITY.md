# Resilience & Observability (Principles 9-10)

This document covers building resilient and observable systems through proper monitoring, logging, and failure isolation.

> **Navigation**: Return to [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | See also [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) | [CODING-PATTERNS.md](./CODING-PATTERNS.md)

## Quick Reference (For LLMs)

**When to use this doc**: Adding logging, monitoring, error handling, or fault tolerance

**Key rules**:

- ✅ DO: Add module-specific logging, metrics, and health checks
- ✅ DO: Use MicroProfile Fault Tolerance (`@CircuitBreaker`, `@Retry`, `@Timeout`) for external service calls
- ✅ DO: Implement graceful degradation with `@Fallback`
- ❌ DON'T: Mix module concerns in logging/monitoring
- ❌ DON'T: Let failures cascade between modules

**Detection**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for detection commands

**See also**:

- [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md) - External API integration patterns
- [CODING-PATTERNS.md](./CODING-PATTERNS.md) - Service implementation patterns
- [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) - Verification steps

## When to Read This Document

**Read this document when:**

- [ ] Adding logging to services
- [ ] Implementing metrics and monitoring
- [ ] Adding health checks
- [ ] Implementing circuit breakers or retries
- [ ] Handling failures and graceful degradation
- [ ] Setting up observability dashboards

**Skip this document if:**

- You're only creating entities/repositories (see [STATE-ISOLATION.md](./STATE-ISOLATION.md) and [CODING-PATTERNS.md](./CODING-PATTERNS.md))
- You're only integrating external APIs (see [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md))
- You're only designing module boundaries (see [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md))

## Table of Contents

- [Observability & Monitoring](#observability--monitoring)
- [Fail Independence](#fail-independence)
- [Recommended Event System Implementations](#recommended-event-system-implementations)

---

## Observability & Monitoring

### Definition

Each module provides individual visibility into its health, performance, and behavior.

### Rules for AI Agents

- ✅ **DO**: Add module-specific logging, metrics, and health checks
- ✅ **DO**: Use consistent logging formats with module identifiers
- ✅ **DO**: Create module-specific dashboards and alerts
- ❌ **DON'T**: Mix module concerns in logging and monitoring
- ❌ **DON'T**: Rely only on application-level monitoring

### Code Examples

#### Module-Specific Logger

```java
// ✅ GOOD: Module-specific logger
// ordering-domain-module/.../OrderService.java
@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    private static final Logger LOG = Logger.getLogger(OrderServiceImpl.class);

    @Transactional
    public OrderId placeOrder(PlaceOrderCommand command) {
        LOG.infof("Placing order for customer=%s, product=%s",
            command.customerId(), command.productId());

        try {
            var order = Order.create(command);
            orderRepository.persist(order.toEntity());

            LOG.infof("Order placed successfully: orderId=%s, customer=%s",
                order.id().value(), command.customerId());

            return order.id();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to place order for customer=%s, product=%s",
                command.customerId(), command.productId());
            throw e;
        }
    }
}
```

#### Module-Specific Health Check

```java
// ✅ GOOD: Module-specific health check using MicroProfile Health
@Readiness
@ApplicationScoped
public class OrderingHealthCheck implements HealthCheck {

    @Inject
    OrderRepository orderRepository;

    @Override
    public HealthCheckResponse call() {
        try {
            // Check database connectivity
            long activeOrders = orderRepository.count("status", OrderStatus.ACTIVE);

            return HealthCheckResponse.named("ordering-module")
                .up()
                .withData("activeOrders", activeOrders)
                .withData("dbConnection", "ok")
                .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("ordering-module")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

#### Module-Specific Metrics

```java
// ✅ GOOD: Module-specific metrics using Micrometer
@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    @Inject
    MeterRegistry registry;

    @Counted(value = "ordering.orders.placed.total",
             description = "Total number of orders placed")
    @Timed(value = "ordering.orders.placed.duration",
           description = "Duration of order placement")
    @Transactional
    public OrderId placeOrder(PlaceOrderCommand command) {
        var order = Order.create(command);
        orderRepository.persist(order.toEntity());

        registry.counter("ordering.orders.placed",
            "status", order.status().name()
        ).increment();

        return order.id();
    }
}
```

---

## Fail Independence

### Definition

Failures in one module don't cascade to other modules, maintaining system resilience.

### Rules for AI Agents

- ✅ **DO**: Use MicroProfile Fault Tolerance for inter-module and external calls
- ✅ **DO**: Design graceful degradation when dependencies fail
- ✅ **DO**: Use `@Timeout`, `@Retry`, and `@CircuitBreaker` annotations
- ❌ **DON'T**: Let one module's failure bring down others
- ❌ **DON'T**: Create synchronous dependencies that can cascade failures

### Code Examples

#### Circuit Breaker with MicroProfile Fault Tolerance

```java
// ✅ GOOD: Circuit breaker for external service calls
@ApplicationScoped
public class ExternalRatingClient {

    private static final Logger LOG = Logger.getLogger(ExternalRatingClient.class);

    @Inject
    @RestClient
    MovieRatingRestClient restClient;

    @CircuitBreaker(
        requestVolumeThreshold = 10,
        failureRatio = 0.5,
        delay = 30000  // 30 seconds before retry
    )
    @Timeout(3000)  // 3 second timeout
    @Retry(maxRetries = 2, delay = 500)
    @Fallback(fallbackMethod = "getRatingFallback")
    public MovieRating getMovieRating(String movieTitle) {
        return restClient.getRating(movieTitle);
    }

    // Graceful degradation fallback
    private MovieRating getRatingFallback(String movieTitle) {
        LOG.warnf("Movie rating service unavailable, using fallback for: %s", movieTitle);
        return new MovieRating(null, "fallback", "Rating service temporarily unavailable");
    }
}
```

#### Async Communication with Failure Handling

```java
// ✅ GOOD: Async communication with failure handling
@ApplicationScoped
public class OrderCompletionService {

    private static final Logger LOG = Logger.getLogger(OrderCompletionService.class);

    @Inject
    OrderRepository orderRepository;

    @Inject
    @Channel("order-events")
    Emitter<OrderPlacedEvent> eventEmitter;

    @Inject
    ExternalRatingClient ratingClient;

    @Transactional
    public void completeOrder(String orderId) {
        // Core operation (always works)
        var order = orderRepository.findByIdOptional(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found"));
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.persist(order);

        // Non-critical: emit event (can fail independently)
        try {
            eventEmitter.send(new OrderPlacedEvent(
                order.getId(), order.getCustomerId(), order.getTotalAmount(), Instant.now()
            ));
        } catch (Exception e) {
            LOG.warn("Failed to emit order event, continuing without event", e);
        }

        // Optional: fetch external rating (graceful degradation)
        try {
            var rating = ratingClient.getMovieRating(order.getProductName());
            if (rating.rating() != null) {
                order.setExternalRating(rating.rating());
                orderRepository.persist(order);
            }
        } catch (Exception e) {
            LOG.warn("External rating fetch failed, continuing without rating", e);
        }
    }
}
```

#### Timeout and Retry Configuration

```java
// ✅ GOOD: REST Client with Fault Tolerance annotations
@RegisterRestClient(configKey = "payment-gateway")
@Path("/v1")
public interface PaymentGatewayRestClient {

    @POST
    @Path("/charges")
    @Timeout(5000)  // 5 second timeout
    @Retry(maxRetries = 3, delay = 500, jitter = 200,
           retryOn = {WebApplicationException.class, ProcessingException.class})
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5)
    PaymentResponse processPayment(PaymentRequest request);
}

// ✅ GOOD: Service with fallback
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @Inject
    @RestClient
    PaymentGatewayRestClient paymentClient;

    public PaymentResult processPayment(PaymentRequest request) {
        try {
            var response = paymentClient.processPayment(request);
            return PaymentResult.success(response.transactionId());
        } catch (Exception e) {
            LOG.error("Payment processing failed after retries", e);
            return PaymentResult.degraded("Payment service temporarily unavailable");
        }
    }
}
```

#### Health Check That Doesn't Cascade Failures

```java
// ✅ GOOD: Health check that doesn't cascade failures
@Readiness
@ApplicationScoped
public class CommerceHealthCheck implements HealthCheck {

    @Inject OrderRepository orderRepository;
    @Inject ExternalRatingClient ratingClient;

    @Override
    public HealthCheckResponse call() {
        boolean dbHealthy = checkDatabase();
        boolean externalHealthy = checkExternal();

        // Module is healthy if core functionality works
        // External dependencies can be degraded
        boolean isHealthy = dbHealthy;

        return HealthCheckResponse.named("commerce-service")
            .status(isHealthy)
            .withData("database", dbHealthy ? "ok" : "failed")
            .withData("externalRating", externalHealthy ? "ok" : "degraded")
            .withData("canOperate", isHealthy)
            .build();
    }

    private boolean checkDatabase() {
        try {
            orderRepository.count();
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean checkExternal() {
        try {
            ratingClient.getMovieRating("health-check");
            return true;
        } catch (Exception e) { return false; }
    }
}
```

---

## Recommended Event System Implementations

For production use, Quarkus Messaging provides reactive messaging with multiple connectors:

### For Local Development/Testing

```java
// Simple CDI event for in-process development
@ApplicationScoped
public class InMemoryEventPublisher {

    @Inject
    Event<OrderPlacedEvent> orderEvent;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        orderEvent.fire(event);
    }
}

// Observer
@ApplicationScoped
public class InvoicingEventObserver {

    public void onOrderPlaced(@Observes OrderPlacedEvent event) {
        // Process event in-memory
    }
}
```

### For Production

#### Kafka Implementation (Quarkus Messaging)

```java
// Kafka publisher using Quarkus Messaging
@ApplicationScoped
public class OrderEventPublisher {

    @Inject
    @Channel("order-events-out")
    Emitter<OrderPlacedEvent> emitter;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        emitter.send(Message.of(event)
            .withMetadata(Metadata.of(
                OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(event.orderId())
                    .withHeaders(new RecordHeaders()
                        .add("eventType", "OrderPlaced".getBytes()))
                    .build()
            )));
    }
}

// Kafka consumer
@ApplicationScoped
public class InvoicingEventConsumer {

    @Incoming("order-events-in")
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Process event from Kafka
    }
}
```

```properties
# application.properties — Kafka connector config
mp.messaging.outgoing.order-events-out.connector=smallrye-kafka
mp.messaging.outgoing.order-events-out.topic=order-events
mp.messaging.outgoing.order-events-out.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer

mp.messaging.incoming.order-events-in.connector=smallrye-kafka
mp.messaging.incoming.order-events-in.topic=order-events
mp.messaging.incoming.order-events-in.value.deserializer=io.quarkus.kafka.client.serialization.ObjectMapperDeserializer
```

#### AMQP / RabbitMQ Implementation

```java
// AMQP publisher
@ApplicationScoped
public class OrderEventPublisher {

    @Inject @Channel("order-events")
    Emitter<OrderPlacedEvent> emitter;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        emitter.send(event);
    }
}
```

```properties
# AMQP connector config
mp.messaging.outgoing.order-events.connector=smallrye-amqp
mp.messaging.outgoing.order-events.address=order-events
```

### Choosing the Right Implementation

| Implementation  | Best For                                       | Quarkus Extension                     |
| --------------- | ---------------------------------------------- | ------------------------------------- |
| **CDI Events**  | In-process dev/testing                         | Built-in                              |
| **Kafka**       | High-throughput, event sourcing                | `quarkus-smallrye-reactive-messaging-kafka` |
| **AMQP**        | RabbitMQ, flexible routing                     | `quarkus-smallrye-reactive-messaging-amqp`  |
| **Redis**       | Fast in-memory pub/sub                         | `quarkus-redis`                       |

**Always prefer proven message systems over CDI events for inter-module communication in production environments.**

---

## Patterns Summary

### Observability Best Practices

1. **Structured Logging**
   - Use JBoss Logging (`Logger.getLogger(Class)`)
   - Include module identifiers in log messages
   - Log at appropriate levels (debug, info, warn, error)
   - Use `LOG.infof()` for parameterized logging

2. **Metrics Collection**
   - Use Micrometer (`@Counted`, `@Timed`, `MeterRegistry`)
   - Track business metrics (orders placed, invoices created)
   - Monitor technical metrics (response times, error rates)

3. **Health Checks**
   - Use MicroProfile Health (`@Readiness`, `@Liveness`)
   - Check database connectivity
   - Monitor external dependencies
   - Return detailed status information

4. **Distributed Tracing**
   - Use OpenTelemetry (auto-instrumented by Quarkus)
   - Track requests through multiple modules
   - Identify performance bottlenecks

### Resilience Best Practices

1. **Circuit Breakers** — `@CircuitBreaker`
   - Protect against cascading failures
   - Use `@Fallback` for graceful degradation
   - Monitor circuit state transitions

2. **Timeouts** — `@Timeout`
   - Set reasonable timeout values
   - Don't wait indefinitely for responses

3. **Retries** — `@Retry`
   - Use exponential backoff (`delay`, `jitter`)
   - Limit retry attempts (`maxRetries`)
   - Only retry idempotent operations

4. **Graceful Degradation**
   - `@Fallback` methods or classes
   - Continue core operations when non-critical services fail
   - Provide reduced functionality rather than complete failure

5. **Bulkheads** — `@Bulkhead`
   - Isolate resource pools per module
   - Prevent resource exhaustion in one module affecting others

---

## Monitoring Checklist

When implementing observability and resilience:

- [ ] Module has structured logging with `Logger.getLogger()`
- [ ] Critical operations emit metrics (`@Counted`, `@Timed`)
- [ ] Health check endpoint implemented (`@Readiness` / `@Liveness`)
- [ ] External service calls have `@CircuitBreaker`
- [ ] All network calls have `@Timeout`
- [ ] Retry logic uses `@Retry` with `delay` and `jitter`
- [ ] Graceful degradation via `@Fallback`
- [ ] Health checks don't cascade failures
- [ ] OpenTelemetry tracing enabled
- [ ] Alerts configured for critical metrics

---

## Detection Commands

**See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for all detection commands.**

**Quick checks for observability**:

```bash
# Services without logging
find . -name '*.java' -path '*/domain/*' | xargs grep -L 'Logger' | grep -i 'service\|usecase'

# Check for Fault Tolerance annotations
grep -rn '@CircuitBreaker\|@Retry\|@Timeout\|@Fallback\|@Bulkhead' --include='*.java' .
```

---

## Summary

### Principles 9-10 Checklist

**Observability (Principle 9)**:

- [ ] Module-specific logger implemented (`Logger.getLogger()`)
- [ ] Business and technical metrics tracked (`@Counted`, `@Timed`)
- [ ] Health check endpoint available (`@Readiness`)
- [ ] Structured logging with module identifiers

**Fail Independence (Principle 10)**:

- [ ] `@CircuitBreaker` for external services
- [ ] `@Timeout` on all network calls
- [ ] `@Retry` with exponential backoff
- [ ] `@Fallback` for graceful degradation
- [ ] Health checks don't cascade failures

---

## Next Steps

- **Coding Patterns**: See [CODING-PATTERNS.md](./CODING-PATTERNS.md) for implementation patterns
- **State Isolation**: See [STATE-ISOLATION.md](./STATE-ISOLATION.md) for database guidelines
- **Implementation**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) for verification steps

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
