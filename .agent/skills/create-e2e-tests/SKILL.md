---
name: create-e2e-tests
description: Creates e2e tests following project patterns and conventions. Use when creating e2e tests, writing e2e tests, adding e2e tests, or when the user mentions end-to-end testing.
---

# Create E2E Tests

## Quick Start

When creating e2e tests, follow this workflow:

1. Determine the feature and module
2. Create test file in correct location
3. Set up Quarkus test with proper annotations
4. Write tests following Arrange-Act-Assert with RestAssured
5. Configure test data setup and cleanup
6. Use test profiles for custom configurations

## Related Skills

- **`quarkus-test-generator`**: For concurrency, resilience, and combinatorial testing patterns (Storm, Cliff, Parameterized).
- **`test-scenario-planner`**: For planning test scenarios and generating coverage matrices before writing test code.

## File Location Pattern

Tests must be placed in:

```
{service}/infrastructure-module/src/test/java/{base/package}/infra/rest/{Feature}ResourceIT.java
```

Or for domain integration tests:

```
{service}/infrastructure-module/src/test/java/{base/package}/{Feature}IT.java
```

**Conventions**:
- Suffix `IT` for integration tests (e.g., `OrderResourceIT.java`)
- Suffix `Test` for unit tests (e.g., `OrderCalculatorTest.java`)
- Place in same package structure as the class under test

## Required Imports Template

Every e2e test needs these standard imports:

```java
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
```

Additional imports as needed:

```java
import io.quarkus.test.junit.QuarkusIntegrationTest;    // For native/packaged tests
import io.quarkus.test.InjectMock;                       // For mocking CDI beans
import io.quarkus.test.common.http.TestHTTPEndpoint;     // For targeting specific resources
import jakarta.inject.Inject;                             // For injecting test dependencies
import jakarta.transaction.Transactional;                 // For test data setup/cleanup
import org.junit.jupiter.api.TestInstance;                // For lifecycle control
```

## Test Class Setup

### Basic @QuarkusTest

```java
@QuarkusTest
class OrderResourceIT {

    @Test
    void should_return_health_ok() {
        given()
            .when()
            .get("/api/orders/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("ok"));
    }
}
```

### With Test Profile

```java
@QuarkusTest
@TestProfile(OrderTestProfile.class)
class OrderResourceIT {
    // Tests with custom config overrides
}

// TestProfile definition
public class OrderTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "some.config.key", "test-value",
            "quarkus.datasource.db-kind", "h2"
        );
    }
}
```

### With Mocked Dependencies

```java
@QuarkusTest
class OrderResourceIT {

    @InjectMock
    ExternalPaymentService paymentService;

    @BeforeEach
    void setUp() {
        Mockito.when(paymentService.charge(Mockito.any()))
            .thenReturn(new PaymentResult("OK", "txn-123"));
    }
}
```

## Lifecycle Hooks

### @BeforeEach — Seed Test Data

```java
@Inject
EntityManager em;

@BeforeEach
@Transactional
void setUp() {
    // Insert test data
    var product = new ProductEntity("PROD-001", "Widget", BigDecimal.TEN, 100);
    em.persist(product);
}
```

### @AfterEach — Clean Up

```java
@AfterEach
@Transactional
void tearDown() {
    // Delete in reverse dependency order (children first)
    em.createQuery("DELETE FROM OrderItemEntity").executeUpdate();
    em.createQuery("DELETE FROM OrderEntity").executeUpdate();
    em.createQuery("DELETE FROM ProductEntity").executeUpdate();
}
```

### @AfterAll — Release Resources

```java
@AfterAll
static void cleanUp() {
    // Only if needed for external resources
}
```

## Test Structure Pattern

Follow Arrange-Act-Assert:

```java
@Test
void should_create_order_successfully() {
    // Arrange
    String requestBody = """
        {
            "customerId": "CLI-001",
            "items": [
                {"productId": "PROD-001", "quantity": 2}
            ]
        }
        """;

    // Act & Assert
    given()
        .contentType("application/json")
        .body(requestBody)
        .when()
        .post("/api/orders")
        .then()
        .statusCode(200)
        .body("customerId", equalTo("CLI-001"))
        .body("status", equalTo("APPROVED"))
        .body("items.size()", greaterThan(0));
}
```

## Database Testing with Dev Services

Quarkus Dev Services automatically provisions test databases (PostgreSQL via Testcontainers). No manual DB setup needed.

**application.properties** (test profile auto-applies):
```properties
# Dev Services auto-configures test datasource
%test.quarkus.datasource.devservices.enabled=true
%test.quarkus.hibernate-orm.database.generation=drop-and-create
```

**For explicit test data setup**, use repository injection:

```java
@QuarkusTest
class InvoiceResourceIT {

    @Inject
    InvoiceRepository invoiceRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        var invoice = new InvoiceEntity();
        invoice.setCustomerId("CLI-001");
        invoice.setAmount(BigDecimal.valueOf(100));
        invoiceRepository.persist(invoice);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        invoiceRepository.deleteAll();
    }
}
```

## Authentication Mocking

### Using Test Security Annotation

```java
@QuarkusTest
class SecuredResourceIT {

    @Test
    @TestSecurity(user = "testuser", roles = {"admin"})
    void should_access_admin_endpoint() {
        given()
            .when()
            .get("/api/admin/dashboard")
            .then()
            .statusCode(200);
    }

    @Test
    void should_reject_unauthenticated_request() {
        given()
            .when()
            .get("/api/admin/dashboard")
            .then()
            .statusCode(401);
    }
}
```

### Using OIDC Test Support

```java
// Add to pom.xml: quarkus-test-oidc-server
@QuarkusTest
@OidcSecurity(claims = {
    @Claim(key = "sub", value = "user-123"),
    @Claim(key = "email", value = "test@example.com")
})
class OidcSecuredResourceIT {
    // Tests run with OIDC mock
}
```

## External API Mocking with WireMock

```java
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class ExternalApiIT {

    @ConfigProperty(name = "external.api.url")
    String apiUrl;

    @Test
    void should_call_external_api() {
        // WireMock stub
        stubFor(get(urlEqualTo("/api/v1/products"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"id": "1", "name": "Widget"}]
                    """)));

        // Test
        given()
            .when()
            .get("/api/orders/products")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }
}
```

**WireMock Resource**:

```java
public class WireMockExtensions implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        return Map.of("external.api.url", wireMockServer.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMockServer != null) wireMockServer.stop();
    }
}
```

## Examples

### Example 1: Simple CRUD Test

```java
@QuarkusTest
class ProductResourceIT {

    @Test
    void should_create_product() {
        String body = """
            {"name": "Widget", "price": 9.99, "quantity": 100}
            """;

        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/products")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Widget"))
            .body("price", equalTo(9.99f));
    }

    @Test
    void should_get_product_by_id() {
        given()
            .when()
            .get("/api/products/{id}", "PROD-001")
            .then()
            .statusCode(200)
            .body("name", notNullValue());
    }
}
```

### Example 2: Error Case Tests

```java
@Test
void should_return_not_found_for_missing_product() {
    given()
        .when()
        .get("/api/products/{id}", "NON-EXISTENT")
        .then()
        .statusCode(404);
}

@Test
void should_reject_invalid_request() {
    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/orders")
        .then()
        .statusCode(400);
}
```

### Example 3: Multi-Step Integration Test

```java
@Test
void should_process_complete_order_flow() {
    // Step 1: Create order
    String orderId = given()
        .contentType("application/json")
        .body("""
            {"customerId": "CLI-001", "items": [{"productId": "PROD-001", "quantity": 1}]}
            """)
        .when()
        .post("/api/orders")
        .then()
        .statusCode(200)
        .body("status", equalTo("APPROVED"))
        .extract().path("orderId");

    // Step 2: Verify order exists
    given()
        .when()
        .get("/api/orders/{id}", orderId)
        .then()
        .statusCode(200)
        .body("customerId", equalTo("CLI-001"));
}
```

## Test Data Factories

Use builder pattern with records for test data:

```java
public class TestDataFactory {

    public static ProductEntity aProduct() {
        return new ProductEntity(
            UUID.randomUUID().toString(),
            "Test Product",
            "Description",
            BigDecimal.TEN,
            100,
            "ELECTRONICS"
        );
    }

    public static ProductEntity aProduct(String name, BigDecimal price) {
        return new ProductEntity(
            UUID.randomUUID().toString(),
            name, "Description", price, 100, "ELECTRONICS"
        );
    }

    public static String orderRequestJson(String customerId, String productId, int qty) {
        return """
            {
                "customerId": "%s",
                "items": [{"productId": "%s", "quantity": %d}]
            }
            """.formatted(customerId, productId, qty);
    }
}
```

## Anti-Patterns to Avoid

**Don't forget database cleanup**: Always clean tables in `@AfterEach` with `@Transactional`

**Don't hardcode IDs**: Use `UUID.randomUUID()` or test data factories

**Don't skip Dev Services**: Let Quarkus auto-provision test databases instead of manual config

**Don't test implementation details**: Test HTTP contracts, not internal service methods

**Don't mix test data**: Each test should be independent — clean up after each test

**Don't forget `@Transactional`**: Required on `@BeforeEach`/`@AfterEach` methods that modify data

**Don't use `@QuarkusIntegrationTest` for module tests**: Reserve for native/packaged testing only; use `@QuarkusTest` for standard integration tests

## Key Dependencies (pom.xml)

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
<!-- Optional: for mocking external HTTP -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5-mockito</artifactId>
    <scope>test</scope>
</dependency>
```
