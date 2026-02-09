package org.acme.cache;

import jakarta.enterprise.context.Dependent;

/**
 * Default implementation of ExpireCondition that never expires.
 */
@Dependent
public class NeverExpire implements ExpireCondition {

    @Override
    public boolean isExpired(String cacheName, String key, Object value) {
        return false;
    }
}
