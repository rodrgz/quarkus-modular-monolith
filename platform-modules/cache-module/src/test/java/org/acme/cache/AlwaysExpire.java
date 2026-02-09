package org.acme.cache;

import jakarta.enterprise.context.Dependent;

/**
 * Test ExpireCondition that always returns true (always expired).
 * Used to test expire condition eviction paths.
 */
@Dependent
public class AlwaysExpire implements ExpireCondition {

    @Override
    public boolean isExpired(String cacheName, String key, Object value) {
        return true;
    }
}
