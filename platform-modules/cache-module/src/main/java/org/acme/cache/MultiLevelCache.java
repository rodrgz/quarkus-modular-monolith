package org.acme.cache;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for multi-level caching with Caffeine (L1) and Redis (L2).
 * 
 * <h2>Cache Levels:</h2>
 * <ul>
 *   <li><b>L1 (Caffeine)</b>: Fast in-memory cache, local to each pod</li>
 *   <li><b>L2 (Redis)</b>: Distributed cache, shared across pods</li>
 * </ul>
 * 
 * <h2>Lookup Order:</h2>
 * <ol>
 *   <li>Check L1 (Caffeine) → if hit, return</li>
 *   <li>Check L2 (Redis) → if hit, populate L1 and return</li>
 *   <li>Call origin method → populate L1 and L2, then return</li>
 * </ol>
 * 
 * <h2>Invalidation:</h2>
 * <p>Uses Redis Pub/Sub to notify all pods when cache entries are invalidated.
 * Each pod subscribes to the invalidation channel and clears its local L1 cache.</p>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @MultiLevelCache(
 *     cacheName = "products",
 *     l1TtlSeconds = 30,
 *     l1MaxSize = 500,
 *     l2TtlSeconds = 300
 * )
 * public Optional<ProductDTO> findById(String productId) {
 *     return repository.findById(productId);
 * }
 * }</pre>
 * 
 * @see MultiLevelCacheInterceptor
 * @see CacheInvalidator
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
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
     * L1 TTL (Time To Live) in seconds.
     * Default: 60 seconds
     */
    @Nonbinding
    long l1TtlSeconds() default 60;
    
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
     * L2 TTL (Time To Live) in seconds.
     * Default: 300 seconds (5 minutes)
     */
    @Nonbinding
    long l2TtlSeconds() default 300;
    
    // ===== Invalidation =====
    
    /**
     * Redis channel for invalidation messages.
     * Default: "cache-invalidation"
     */
    @Nonbinding
    String invalidationChannel() default "cache-invalidation";
}
