package org.acme.cache;

import jakarta.enterprise.context.Dependent;

/**
 * Test ExpireCondition with a static flag to control expiration.
 * Used to test dynamic expiration scenarios.
 */
@Dependent
public class ConditionalExpire implements ExpireCondition {

    public static volatile boolean shouldExpire = false;

    @Override
    public boolean isExpired(String cacheName, String key, Object value) {
        return shouldExpire;
    }
}
