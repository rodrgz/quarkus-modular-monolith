# Third-Party Integration Documentation

Patterns and best practices for integrating external APIs and services.

> **Navigation**: Return to [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | See also [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md)

## Quick Reference (For LLMs)

**When to use this doc**: Integrating external APIs, third-party services, or REST clients

**Key rules**:

- ✅ DO: Encapsulate ALL HTTP/API details in client class or `@RegisterRestClient`
- ✅ DO: Use MicroProfile Fault Tolerance (`@Timeout`, `@Retry`, `@CircuitBreaker`)
- ✅ DO: Store API keys via MicroProfile Config (`@ConfigProperty`)
- ❌ DON'T: Expose HTTP details to services
- ❌ DON'T: Hardcode API keys or share clients across modules

**Detection**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for detection commands

**See also**:

- [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) - Circuit breaker and failure patterns
- [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) - Module boundaries and replaceability
- [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) - Verification steps

## When to Read This Document

**Read this document when:**

- [ ] Integrating external REST APIs
- [ ] Using vendor SDKs
- [ ] Creating REST clients
- [ ] Implementing mock clients for testing
- [ ] Configuring API authentication and security
- [ ] Migrating from mock to production clients

**Skip this document if:**

- You're only working with internal module communication (see [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md))
- You're only creating entities/repositories (see [STATE-ISOLATION.md](./STATE-ISOLATION.md) and [CODING-PATTERNS.md](./CODING-PATTERNS.md))
- You're only adding logging/monitoring (see [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md))

## Table of Contents

- [Integration Patterns](#integration-patterns)
- [Client Encapsulation](#client-encapsulation)
- [Injection Patterns](#injection-patterns)
- [Implementation Checklist](#implementation-checklist)
- [Code Examples](#code-examples)
- [Anti-Patterns](#anti-patterns)

---

## Integration Patterns

### Pattern 1: Mock Client

**When**: Development, testing, or when external service unavailable

```java
@ApplicationScoped
@Named("mock")
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger LOG = Logger.getLogger(MockPaymentGateway.class);

    public PaymentResponse processPayment(PaymentRequest request) {
        simulateLatency();

        if (Math.random() < 0.1) { // 10% failure rate
            return new PaymentResponse(false, null, "Card declined");
        }

        return new PaymentResponse(true,
            "PAY-" + System.currentTimeMillis(), null);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(100 + (long)(Math.random() * 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### Pattern 2: Quarkus REST Client

**When**: Production REST API integrations

```java
// REST Client interface (auto-implemented by Quarkus)
@RegisterRestClient(configKey = "external-rating")
@Path("/api/v1")
public interface ExternalRatingRestClient {

    @GET
    @Path("/rating/{title}")
    @Timeout(5000)
    RatingResponse getRating(@PathParam("title") String title);
}

// Adapter wrapping the REST Client
@ApplicationScoped
public class ExternalRatingAdapter {

    @Inject
    @RestClient
    ExternalRatingRestClient restClient;

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5)
    @Fallback(fallbackMethod = "fallbackRating")
    public Optional<Double> getRating(String title) {
        try {
            var response = restClient.getRating(title);
            return Optional.ofNullable(response.rating());
        } catch (WebApplicationException e) {
            return Optional.empty();
        }
    }

    private Optional<Double> fallbackRating(String title) {
        return Optional.empty(); // Graceful degradation
    }
}
```

```properties
# application.properties — REST Client config
quarkus.rest-client.external-rating.url=https://api.ratings.example.com
quarkus.rest-client.external-rating.scope=jakarta.inject.Singleton
```

### Pattern 3: SDK Client

**When**: Vendor provides official SDK

```java
@ApplicationScoped
public class GeminiSummaryAdapter implements VideoSummaryGateway {

    @ConfigProperty(name = "gemini.api-key")
    String apiKey;

    @Override
    @Timeout(30000)
    @Retry(maxRetries = 2)
    public String generateSummary(String videoUrl) {
        var ai = new GoogleGenAI(apiKey);
        var result = ai.models().generateContent(
            "gemini-2.0-flash",
            List.of(new Content(videoUrl, "Summarize this video"))
        );
        return result.text();
    }
}
```

---

## Client Encapsulation

**CORE PRINCIPLE**: Client owns ALL HTTP/API details. Services only know business operations.

**Client owns**:

- ✅ URLs and endpoints
- ✅ Authentication (headers, tokens)
- ✅ Request/response mapping
- ✅ Error handling
- ✅ Timeouts and retries

**Service knows**:

- ✅ Business operations only
- ❌ NO HTTP details
- ❌ NO API URLs
- ❌ NO authentication

```java
// ✅ GOOD: REST Client encapsulates everything
@RegisterRestClient(configKey = "payment-gateway")
@Path("/v1/charges")
public interface PaymentGatewayRestClient {

    @POST
    @ClientHeaderParam(name = "Authorization", value = "${payment-gateway.auth-header}")
    @Timeout(10000)
    PaymentApiResponse processPayment(PaymentApiRequest request);
}

// ✅ GOOD: Adapter maps between domain and API
@ApplicationScoped
public class PaymentGatewayAdapter implements PaymentGateway {

    @Inject @RestClient
    PaymentGatewayRestClient restClient;

    @Override
    public PaymentResult processPayment(PaymentRequest domainRequest) {
        var apiRequest = PaymentApiRequest.from(domainRequest);
        var apiResponse = restClient.processPayment(apiRequest);
        return PaymentResult.from(apiResponse);
    }
}

// ✅ GOOD: Service only knows business operations
@ApplicationScoped
public class PaymentService {

    @Inject
    PaymentGateway paymentGateway; // Interface only

    @Transactional
    public Payment processPayment(Invoice invoice) {
        // No HTTP details — just business logic
        var result = paymentGateway.processPayment(
            new PaymentRequest(invoice.totalAmount(), invoice.id())
        );

        return paymentRepository.save(
            new PaymentEntity(result.transactionId(), invoice.totalAmount())
        );
    }
}
```

---

## Injection Patterns

### Default: Direct Injection (Most Common)

**Use when**: Single provider, no plans to replace

```java
@ApplicationScoped
public class TaxService {

    @Inject
    EasyTaxClient easyTaxClient; // Direct injection
}
```

### Interface Pattern: Only When Replaceability Needed

**Use when**: Multiple providers possible (e.g., Gemini vs OpenAI)

```java
// Port interface (domain-api)
public interface VideoSummaryGateway {
    String generateSummary(String videoUrl);
}

// Implementation #1
@ApplicationScoped
@Named("gemini")
public class GeminiSummaryAdapter implements VideoSummaryGateway {
    @Override
    public String generateSummary(String videoUrl) { /* Gemini SDK */ }
}

// Implementation #2
@ApplicationScoped
@Named("openai")
public class OpenAISummaryAdapter implements VideoSummaryGateway {
    @Override
    public String generateSummary(String videoUrl) { /* OpenAI SDK */ }
}

// CDI producer selects implementation based on config
@ApplicationScoped
public class SummaryGatewayProducer {

    @ConfigProperty(name = "summary.provider", defaultValue = "gemini")
    String provider;

    @Inject @Named("gemini") VideoSummaryGateway gemini;
    @Inject @Named("openai") VideoSummaryGateway openai;

    @Produces @ApplicationScoped
    public VideoSummaryGateway summaryGateway() {
        return "openai".equals(provider) ? openai : gemini;
    }
}

// Service uses interface
@ApplicationScoped
public class GenerateSummaryUseCase {

    @Inject
    VideoSummaryGateway gateway; // Interface — implementation injected by CDI
}
```

---

## Implementation Checklist

### Setup

- [ ] Choose pattern (mock, REST Client, SDK)
- [ ] Create client class or `@RegisterRestClient` interface
- [ ] Encapsulate ALL HTTP/API details in client
- [ ] Add configuration (`@ConfigProperty`, `application.properties`)
- [ ] **Default**: Direct injection (NO interface)
- [ ] **Only if needed**: Port interface for replaceability

### Resilience

- [ ] `@Timeout` configuration (5-30 seconds)
- [ ] `@Retry` logic (3 retries, exponential backoff via `delay` + `jitter`)
- [ ] `@CircuitBreaker` for critical external calls
- [ ] `@Fallback` for graceful degradation
- [ ] Error handling (don't cascade failures)

### Observability

- [ ] Structured logging (sanitize sensitive data)
- [ ] Log request/response (no API keys, tokens)
- [ ] Track metrics (`@Counted`, `@Timed`)
- [ ] Include OpenTelemetry tracing

### Security

- [ ] `@ConfigProperty` for API keys (never hardcode)
- [ ] Use `application.properties` or environment variables for credentials
- [ ] Sanitize logs (no sensitive data)
- [ ] Validate SSL certificates

---

## Code Examples

### Example 1: Mock Client (Direct Injection)

```java
@ApplicationScoped
public class EasyTaxClient {

    private static final Logger LOG = Logger.getLogger(EasyTaxClient.class);

    @Timeout(5000)
    public TaxResponse createTransaction(TaxRequest request) {
        simulateLatency();

        if (Math.random() < 0.05) {
            throw new WebApplicationException("EasyTax API error", 500);
        }

        return new TaxResponse(
            request.amount().multiply(BigDecimal.valueOf(0.08)),
            "EASY-" + System.currentTimeMillis()
        );
    }

    private void simulateLatency() {
        try { Thread.sleep(100 + (long)(Math.random() * 200)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

// Service uses directly
@ApplicationScoped
public class TaxService {

    @Inject
    EasyTaxClient easyTaxClient;

    public BigDecimal calculateTax(BigDecimal amount) {
        var response = easyTaxClient.createTransaction(new TaxRequest(amount));
        return response.totalTax();
    }
}
```

### Example 2: REST Client with Fault Tolerance

```java
@RegisterRestClient(configKey = "external-rating")
@Path("/api/v1")
public interface ExternalRatingRestClient {

    @GET
    @Path("/rating/{title}")
    RatingResponse getRating(@PathParam("title") String title);
}

@ApplicationScoped
public class ExternalRatingAdapter {

    private static final Logger LOG = Logger.getLogger(ExternalRatingAdapter.class);

    @Inject @RestClient
    ExternalRatingRestClient restClient;

    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 30000)
    @Timeout(3000)
    @Retry(maxRetries = 2, delay = 500)
    @Fallback(fallbackMethod = "fallbackRating")
    public Optional<Double> getRating(String title) {
        var response = restClient.getRating(title);
        return Optional.ofNullable(response.rating());
    }

    private Optional<Double> fallbackRating(String title) {
        LOG.warnf("Rating API failed for '%s', using fallback", title);
        return Optional.empty(); // Graceful degradation
    }
}
```

### Example 3: SDK Client with Interface

```java
// Port interface
public interface VideoSummaryGateway {
    String generateSummary(String videoUrl);
}

// SDK client implementation
@ApplicationScoped
@Named("gemini")
public class GeminiSummaryAdapter implements VideoSummaryGateway {

    @ConfigProperty(name = "gemini.api-key")
    String apiKey;

    @Override
    @Timeout(30000)
    @Retry(maxRetries = 2)
    public String generateSummary(String videoUrl) {
        var ai = new GoogleGenAI(apiKey);
        var result = ai.models().generateContent("gemini-2.0-flash",
            List.of(new Content(videoUrl, "Summarize this video")));
        return result.text();
    }
}

// Use case
@ApplicationScoped
public class GenerateSummaryUseCase {

    @Inject
    VideoSummaryGateway gateway;

    @Transactional
    public void execute(Video video) {
        String summary = gateway.generateSummary(video.getUrl());
        video.setSummary(summary);
        videoRepository.persist(video);
    }
}
```

---

## Anti-Patterns

### ❌ DON'T: Hardcode API Keys

```java
// ❌ BAD
private final String apiKey = "sk_live_1234567890";

// ✅ GOOD
@ConfigProperty(name = "payment.api-key")
String apiKey;
```

### ❌ DON'T: Expose HTTP Details to Services

```java
// ❌ BAD: Service knows HTTP details
public PaymentResult processPayment(Invoice invoice) {
    var response = httpClient.target("https://api.stripe.com/v1/charges")
        .request()
        .header("Authorization", "Bearer " + apiKey)
        .post(Entity.json(data));  // ❌ HTTP details in service
}

// ✅ GOOD: Client encapsulates HTTP
public PaymentResult processPayment(Invoice invoice) {
    return paymentGateway.processPayment(
        new PaymentRequest(invoice.totalAmount()));  // ✅ Business operation
}
```

### ❌ DON'T: Let Failures Cascade

```java
// ❌ BAD: Failure cascades
public void processInvoice(Invoice invoice) {
    var tax = taxClient.calculateTax(invoice);     // If fails, whole fails
    var payment = paymentGateway.processPayment(invoice);
}

// ✅ GOOD: Graceful degradation
public void processInvoice(Invoice invoice) {
    BigDecimal tax = BigDecimal.ZERO;
    try {
        tax = taxClient.calculateTax(invoice);
    } catch (Exception e) {
        LOG.warn("Tax calculation failed, using default", e);
        tax = calculateDefaultTax(invoice); // Fallback
    }

    // Continue even if tax failed
    var payment = paymentGateway.processPayment(invoice);
}
```

### ❌ DON'T: Share Clients Across Modules

```java
// ❌ BAD: Injecting client from another module's package
import org.acme.billing.infra.client.EasyTaxClient; // ❌ Cross-module access

// ✅ GOOD: Use module gateway/facade
import org.acme.billing.domain.api.TaxGateway; // ✅ Public port interface
```

### ❌ DON'T: Log Sensitive Data

```java
// ❌ BAD
LOG.info("API call: apiKey=" + apiKey + ", card=" + payment.getCardNumber());

// ✅ GOOD: Sanitize
LOG.infof("API call: invoiceId=%s, amount=%s", payment.getInvoiceId(), payment.getAmount());
```

---

## Detection Commands

**See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for all detection commands.**

**Quick checks for third-party integration**:

```bash
# Hardcoded API keys (CRITICAL security issue)
grep -rn 'apiKey\|api-key\|API_KEY' --include='*.java' . | grep -v '@ConfigProperty\|//\|import'

# Clients imported from other modules (violation)
grep -rn 'import.*\.infra\.client\.' --include='*.java' . | grep -v '/test/'
```

---

## Summary

### Quick Reference

- **Patterns**: Mock (dev/test), REST Client (REST APIs), SDK (vendor APIs)
- **Encapsulation**: ALL HTTP/API details in client class
- **Injection**: Direct (default), Port interface (only for replaceability)
- **Resilience**: `@Timeout`, `@Retry`, `@CircuitBreaker`, `@Fallback`
- **Security**: `@ConfigProperty`, `application.properties`, sanitized logs

### Checklist

- [ ] Client encapsulates ALL HTTP/API details
- [ ] Direct injection (default) or port interface (only if replaceable)
- [ ] `@Timeout` configured
- [ ] Error handling (don't cascade failures)
- [ ] `@CircuitBreaker` for critical services
- [ ] No hardcoded API keys (`@ConfigProperty`)
- [ ] Sensitive data not logged
- [ ] Clients within module boundaries

---

## Next Steps

- **Resilience**: See [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) for circuit breaker patterns
- **Modular Principles**: See [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) for Principle 6 (Replaceability)
- **Coding Patterns**: See [CODING-PATTERNS.md](./CODING-PATTERNS.md) for service patterns

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
