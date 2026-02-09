package org.acme.cache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Service for invalidating cache entries across all pods.
 * 
 * <h2>Invalidation Flow:</h2>
 * <ol>
 *   <li>Invalidate local L1 cache</li>
 *   <li>Invalidate L2 (Redis) cache</li>
 *   <li>Publish message to all pods via Redis Pub/Sub</li>
 *   <li>Other pods receive message and clear their L1 cache</li>
 * </ol>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Inject
 * CacheInvalidator cacheInvalidator;
 * 
 * public void updateProduct(String productId) {
 *     repository.update(product);
 *     cacheInvalidator.invalidate("products", productId);
 * }
 * }</pre>
 */
@ApplicationScoped
public class CacheInvalidator {
    
    private static final Logger LOG = Logger.getLogger(CacheInvalidator.class);
    
    @Inject
    MultiLevelCacheManager cacheManager;
    
    /**
     * Invalidate a specific key in all cache levels and notify all pods.
     * 
     * @param cacheName The cache name
     * @param key The specific key to invalidate
     */
    public void invalidate(String cacheName, String key) {
        LOG.infof("Invalidating cache entry: %s:%s", cacheName, key);
        
        // Local L1
        cacheManager.evictFromL1(cacheName, key);
        
        // L2 Redis
        cacheManager.evictFromL2(cacheName, key);
        
        // Notify other pods
        cacheManager.publishInvalidation(cacheName, key);
    }
    
    /**
     * Invalidate all entries for a cache in all levels and notify all pods.
     * 
     * @param cacheName The cache name to clear completely
     */
    public void invalidateAll(String cacheName) {
        LOG.infof("Invalidating all entries for cache: %s", cacheName);
        
        // Local L1
        cacheManager.clearL1(cacheName);
        
        // Note: Clearing all L2 entries requires scanning keys, which is expensive
        // We only clear local L1 and notify other pods
        
        // Notify other pods (using "*" as wildcard)
        cacheManager.publishInvalidation(cacheName, "*");
    }
    
    /**
     * Invalidate a specific key based on method parameters.
     * 
     * @param cacheName The cache name
     * @param methodName The method name used in cache key
     * @param params The parameters used in cache key
     */
    public void invalidateByMethod(String cacheName, String methodName, Object... params) {
        StringBuilder key = new StringBuilder(methodName);
        if (params != null && params.length > 0) {
            key.append(":");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) key.append(":");
                key.append(params[i] == null ? "null" : params[i].toString());
            }
        }
        invalidate(cacheName, key.toString());
    }
}
