package org.acme.cache;

/**
 * Condition to determine if a cached value is expired based on business logic.
 * <p>
 * This check is performed AFTER retrieving the value from the cache (L1 or L2)
 * but BEFORE returning it to the caller.
 * </p>
 */
public interface ExpireCondition {

    /**
     * Checks if the cached value should be considered expired.
     * 
     * @param cacheName The name of the cache
     * @param key       The cache key
     * @param value     The cached value
     * @return true if the entry is expired and should be refreshed; false
     *         otherwise.
     */
    boolean isExpired(String cacheName, String key, Object value);
}
