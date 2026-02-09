---
name: quarkus-test-generator
description: Generates robust JUnit 5 integration and unit tests for Quarkus applications, specializing in concurrency, resilience, and combinatorial testing. Use when implementing test scenarios or ensuring high code coverage.
---

# Quarkus Test Generator

This skill provides expert-level guidance for writing high-quality tests in a Quarkus environment. It emphasizes testing reliability, concurrency handling, and resilience patterns.

## When to Use

- **Implementing Test Plans**: Converting a `test-scenario-planner` matrix into Java code.
- **Testing Concurrency**: Verifying thread safety and race conditions.
- **Testing Resilience**: verifying timeouts, circuit breakers, and fallbacks.
- Triggered by: "write tests", "implement scenarios", "test concurrency", "resilience test".

## Related Skills

- **`test-scenario-planner`**: Use first to generate the scenario matrix, then this skill to implement it.
- **`create-e2e-tests`**: For HTTP/REST endpoint contract testing with RestAssured.

## Core Principles

1.  **Test Real Components**: Prefer `@QuarkusTest` integration tests over pure unit tests with mocks, unless testing isolated logic.
2.  **Verify Concurrency**: Don't assume thread safety; prove it with `CountDownLatch` and multiple threads.
3.  **Test Failures**: Verify how the system behaves when dependencies (Redis, DB) are down or slow.
4.  **Clean State**: Ensure tests don't pollute each other (use `@BeforeEach` to clear state).
5.  **Descriptive Names**: Test method names must be self-descriptive. Never embed planning IDs (e.g., `test_GL04_...`). Use `should_call_origin_once_under_concurrent_load()` instead.

## Key Patterns

### 1. Concurrency Testing (Storm Pattern)

Use this pattern to test for race conditions (e.g., duplicate processing, lost updates).

```java
@Test
public void testConcurrencyStrategy() throws InterruptedException {
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        new Thread(() -> {
            try {
                startLatch.await(); // Wait for signal
                // --- CRITICAL SECTION START ---
                service.performAction();
                // --- CRITICAL SECTION END ---
                successCount.incrementAndGet();
            } catch (Exception e) {
                // handle
            } finally {
                doneLatch.countDown();
            }
        }).start();
    }

    startLatch.countDown(); // Release all threads
    boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
    assertTrue(finished, "Test timed out");
    
    // Assert expected outcome (e.g., only 1 success for mutual exclusion)
    assertEquals(1, successCount.get()); 
}
```

### 2. Resilience Testing (Cliff Pattern)

Test how the system behaves at system boundaries (timeouts, failures).

```java
@Test
public void testResilience_DependencyDown() {
    // Simulate dependency failure (if possible via DevServices or Spy)
    // Or use a specific profile that constructs a faulty bean
    
    // Expect graceful degradation, not 500 Error
    String result = service.getResilientData();
    assertNull(result); // or specific fallback value
    
    // Verify specific exception was handled/logged
}
```

### 3. Parameterized / Combinatorial Testing

Use `@ParameterizedTest` for covering data variations.

```java
@ParameterizedTest
@CsvSource({
    "valid-key, value, true",
    "null-key, value, false",
    "valid-key, null, false"
})
public void testCombinations(String key, String value, boolean expected) {
    if ("null".equals(key)) key = null;
    if ("null".equals(value)) value = null;
    
    assertEquals(expected, service.isValid(key, value));
}
```

### 4. Messaging Testing (Kafka / AMQP)

Use `@InjectSink` and `@InjectSource` from SmallRye Reactive Messaging test utilities to verify producers and consumers without a running broker.

```java
@QuarkusTest
class OrderEventConsumerTest {

    @InjectSink("orders-out")
    InMemorySink<OrderEvent> sink;

    @Inject
    InMemoryConnector connector;

    @BeforeEach
    void setup() {
        sink.clear();
    }

    @Test
    void should_process_valid_order_event() {
        InMemorySource<String> source = connector.source("orders-in");
        source.send("""
            {"orderId": "ORD-1", "status": "CREATED"}
            """);

        await().atMost(Duration.ofSeconds(5))
            .until(() -> sink.received().size() == 1);

        OrderEvent result = sink.received().get(0).getPayload();
        assertEquals("PROCESSED", result.status());
    }

    @Test
    void should_handle_poison_pill_without_crashing() {
        InMemorySource<String> source = connector.source("orders-in");
        source.send("not-valid-json");

        // Consumer should survive and process next message
        source.send("""
            {"orderId": "ORD-2", "status": "CREATED"}
            """);

        await().atMost(Duration.ofSeconds(5))
            .until(() -> sink.received().size() >= 1);
    }

    @Test
    void should_be_idempotent_on_duplicate_delivery() {
        InMemorySource<String> source = connector.source("orders-in");
        String message = """
            {"orderId": "ORD-3", "status": "CREATED"}
            """;

        source.send(message);
        source.send(message); // duplicate

        await().atMost(Duration.ofSeconds(5))
            .until(() -> sink.received().size() >= 1);

        // Verify side effect happened only once
        // (e.g., DB record count, external call count)
    }
}
```

**Key assertions for messaging tests:**
- Consumer survives malformed messages (no crash loop)
- Duplicate messages produce the same end state (idempotency)
- Processing order doesn't matter when messages can arrive out-of-order
- DLQ receives unprocessable messages when configured

## Checklist for Generated Tests

- [ ] Does the test class have `@QuarkusTest`?
- [ ] Are external dependencies (Redis, DB) managed (DevServices)?
- [ ] Is state cleared between tests (`@BeforeEach`)?
- [ ] Are assertions descriptive (`assertEquals(expected, actual, "Message")`)?
- [ ] Are async operations waited for correctly (no `Thread.sleep` unless mocking latency)?
