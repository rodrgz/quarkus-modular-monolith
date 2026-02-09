# Cache Module

A robust multi-level caching solution for Quarkus applications, combining local in-memory caching (Caffeine) with distributed caching (Redis) to provide high performance and consistency across microservices.

## Features

- **Multi-Level Caching**:
  - **L1 (Local)**: Caffeine in-memory cache for ultra-low latency.
  - **L2 (Distributed)**: Redis cache for sharing state across pods.
- **Distributed Invalidation**: Automatically clears L1 caches across all pods when an entry is updated or deleted.
- **Thundering Herd Protection**: Synchronizes concurrent cache misses for the same key ensuring the origin method is called only once.
- **Fencing Tokens**: Prevents stale writes from delayed processes using monotonic tokens.
- **Null Caching**: Optional support for caching null values to prevent repeated database hits for missing resources.
- **Extensible Expiration**: Support for custom business-logic based expiration policies.

## Usage

### Basic Caching

Use the `@MultiLevelCache` annotation on methods you want to cache.

```java
@Inject
ProductRepository repository;

@MultiLevelCache(
    cacheName = "products",
    l1Ttl = 60,                // L1: 60 seconds
    l2Ttl = 300                // L2: 5 minutes
)
public ProductDTO getProduct(String sku) {
    return repository.findBySku(sku);
}
```

### Invalidating Cache

You can invalidate cache entries imperatively using `CacheInvalidator` or declaratively using `@MultiCacheInvalidator`.

#### Declarative Invalidation

```java
@MultiCacheInvalidator(cacheName = "products")
public void updateProduct(ProductDTO product) {
    repository.update(product);
}
```

#### Imperative Invalidation

```java
@Inject
CacheInvalidator invalidator;

public void updateProduct(String sku, ProductDTO product) {
    repository.update(sku, product);
    // Invalidates "products:{sku}" in L1/L2 and notifies other pods
    invalidator.invalidate("products", sku);
}
```

### Fenced Locking (Advanced)

For critical sections where you need to ensure strong consistency and prevent stale writes (e.g., during high concurrency or GC pauses), enable fencing.

```java
@MultiLevelCache(cacheName = "inventory", fenced = true)
public InventoryStatus checkInventory(String sku) {
    // ...
}
```

## Configuration

The module is configured via annotations, but underlying behavior relies on Quarkus Redis and Caffeine properties.

| Annotation Field | Default | Description |
|------------------|---------|-------------|
| `cacheName`      | Required| Unique name for the cache region. |
| `l1Enabled`      | `true`  | Enable local Caffeine cache. |
| `l1Ttl`          | `60`    | Time-to-live for L1 entries (seconds). |
| `l1MaxSize`      | `1000`  | Max entries in L1 memory. |
| `l2Enabled`      | `true`  | Enable distributed Redis cache. |
| `l2Ttl`          | `300`   | Time-to-live for L2 entries (seconds). |
| `cacheNulls`     | `false` | Cache null results (prevents repeated misses). |
| `fenced`         | `false` | Enable fencing token validation. |

### Application Properties

Ensure Redis is configured in your `application.properties`:

```properties
quarkus.redis.hosts=redis://localhost:6379
```

## Architecture

1. **Read Path**:
   - Check L1 (Memory) -> Return if found.
   - Check L2 (Redis) -> If found, populate L1 and return.
   - Execute Method -> Store result in L2 and L1 -> Return.

2. **Write/Invalidate Path**:
   - Update Data.
   - Publish Invalidation Message (Redis Pub/Sub).
   - All subscribers (pods) receive message and evict key from their L1.

## Metrics

The module integrates with Micrometer/OpenTelemetry to provide metrics on:
- L1/L2 Hits and Misses
- Invalidation events
- Cache size
