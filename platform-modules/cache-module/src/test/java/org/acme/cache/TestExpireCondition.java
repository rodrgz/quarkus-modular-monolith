package org.acme.cache;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestExpireCondition implements ExpireCondition {

    /**
     * Static field to allow tests to dynamically control expiration.
     * Set to true to force all values to expire, false to use value-based logic.
     */
    public static volatile boolean shouldExpire = false;

    @Override
    public boolean isExpired(String cacheName, String key, Object value) {
        // If shouldExpire is set, force expiration
        if (shouldExpire) {
            return true;
        }

        // Check value because key is hashed (SHA-256) by CacheKeyGenerator
        if (value instanceof String strValue) {
            return strValue.contains("expired-key");
        }
        return false;
    }
}
