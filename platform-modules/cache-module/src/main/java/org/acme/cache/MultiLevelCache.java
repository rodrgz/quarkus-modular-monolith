package org.acme.cache;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for multi-level caching with Caffeine (L1) and Redis (L2).
 * 
 * <h2>Cache Levels:</h2>
 * <ul>
 * <li><b>L1 (Caffeine)</b>: Fast in-memory cache, local to each pod</li>
 * <li><b>L2 (Redis)</b>: Distributed cache, shared across pods</li>
 * </ul>
 * 
 * <h2>Lookup Order:</h2>
 * <ol>
 * <li>Check L1 (Caffeine) → if hit, return</li>
 * <li>Check L2 (Redis) → if hit, populate L1 and return</li>
 * <li>Call origin method → populate L1 and L2, then return</li>
 * </ol>
 * 
 * <h2>Invalidation:</h2>
 * <p>
 * Uses Redis Pub/Sub to notify all pods when cache entries are invalidated.
 * Each pod subscribes to the invalidation channel and clears its local L1
 * cache.
 * </p>
 * 
 * <h2>Usage Example:</h2>
 * 
 * <pre>{@code
 * @MultiLevelCache(cacheName = "products", l1TtlSeconds = 30, l1MaxSize = 500, l2TtlSeconds = 300)
 * public Optional<ProductDTO> findById(String productId) {
 *     return repository.findById(productId);
 * }
 * }</pre>
 * 
 * @see MultiLevelCacheInterceptor
 * @see CacheInvalidator
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@InterceptorBinding
public @interface MultiLevelCache {

    /**
     * Cache name (used as key prefix).
     * Format: {cacheName}:{method parameters hash}
     */
    @Nonbinding
    String cacheName();

    // ===== L1 Configuration (Caffeine) =====

    /**
     * Enable L1 (in-memory) cache.
     * Default: true
     */
    @Nonbinding
    boolean l1Enabled() default true;

    /**
     * L1 TTL (Time To Live).
     * Default: 60
     */
    @Nonbinding
    long l1Ttl() default 60;

    /**
     * L1 TTL Unit.
     * Default: SECONDS
     */
    @Nonbinding
    TimeUnit l1TtlUnit() default TimeUnit.SECONDS;

    /**
     * L1 maximum number of entries.
     * Default: 1000 entries
     */
    @Nonbinding
    int l1MaxSize() default 1000;

    /**
     * Use L1 as fallback when L2 (Redis) is unavailable.
     * Default: true
     */
    @Nonbinding
    boolean l1AsFallback() default true;

    // ===== L2 Configuration (Redis) =====

    /**
     * Enable L2 (Redis) cache.
     * Default: true
     */
    @Nonbinding
    boolean l2Enabled() default true;

    /**
     * L2 TTL (Time To Live).
     * Default: 300
     */
    @Nonbinding
    long l2Ttl() default 300;

    /**
     * L2 TTL Unit.
     * Default: SECONDS
     */
    @Nonbinding
    TimeUnit l2TtlUnit() default TimeUnit.SECONDS;

    // ===== Invalidation =====

    /**
     * Redis channel for invalidation messages.
     * Default: "cache-invalidation"
     */
    @Nonbinding
    String invalidationChannel() default "cache-invalidation";

    /**
     * Condition for business-logic based expiration.
     * Default: NeverExpire (always valid usually).
     */
    @Nonbinding
    Class<? extends ExpireCondition> expireWhen() default NeverExpire.class;

    // ===== Null Caching =====

    /**
     * Cache null results to prevent thundering herd on missing keys.
     * When enabled, null origin results are cached as sentinel values
     * with a separate (shorter) TTL.
     * Default: false
     */
    @Nonbinding
    boolean cacheNulls() default false;

    /**
     * TTL for null sentinel entries.
     * Should typically be shorter than the main TTL.
     * Default: 30
     */
    @Nonbinding
    long nullTtl() default 30;

    /**
     * Null TTL unit.
     * Default: SECONDS
     */
    @Nonbinding
    TimeUnit nullTtlUnit() default TimeUnit.SECONDS;

    // ===== Fencing =====

    /**
     * Enable fencing token validation on cache writes.
     * When enabled, cache writes include a monotonically increasing
     * token to prevent stale writes from delayed processes.
     * Default: false
     */
    @Nonbinding
    boolean fenced() default false;
}
