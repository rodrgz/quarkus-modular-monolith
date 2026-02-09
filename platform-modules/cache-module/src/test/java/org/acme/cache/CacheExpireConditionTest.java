package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for custom ExpireCondition paths in MultiLevelCacheManager.
 * Covers H-1 and H-2 from coverage gap analysis.
 */
@QuarkusTest
public class CacheExpireConditionTest {

    @Inject
    MultiLevelCacheManager cacheManager;

    @BeforeEach
    void setup() {
        ConditionalExpire.shouldExpire = false;
    }

    // ========== H-1: get() with expire condition (L177-179, L202-204) ==========

    @Test
    void test_H1_get_expireConditionL1_evictsFromL1() {
        // Register cache with AlwaysExpire condition
        MultiLevelCacheManager.CacheConfig config = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true, true, 300L, AlwaysExpire.class, false, 30L);
        cacheManager.registerCache("expire-l1-test", config);

        // Put value directly in cache
        cacheManager.put("expire-l1-test", "key1", "value1");

        // Call get() - should detect expiration and evict from L1, return null
        String result = cacheManager.get("expire-l1-test", "key1", String.class);

        assertThat(result).isNull();
        // Verify it was evicted by trying to get raw value
        String raw = cacheManager.getRaw("expire-l1-test", "key1");
        assertThat(raw).isNull();
    }

    @Test
    void test_H1_get_expireConditionL2_evictsFromL2() {
        // Register cache with AlwaysExpire condition, L1 disabled to test L2 path
        MultiLevelCacheManager.CacheConfig config = new MultiLevelCacheManager.CacheConfig(
                false, 60L, 1000, true, true, 300L, AlwaysExpire.class, false, 30L);
        cacheManager.registerCache("expire-l2-test", config);

        // Put value directly in cache
        cacheManager.put("expire-l2-test", "key2", "value2");

        // Call get() - should detect expiration in L2 and evict, return null
        String result = cacheManager.get("expire-l2-test", "key2", String.class);

        assertThat(result).isNull();
        // Verify it was evicted from L2
        String raw = cacheManager.getRaw("expire-l2-test", "key2");
        assertThat(raw).isNull();
    }

    // ========== H-2: getOrLoad() with expire condition (L280-294) ==========

    @Test
    void test_H2_getOrLoad_expireConditionTriggered_evictsAndReturnsNull() {
        // Register cache with ConditionalExpire
        MultiLevelCacheManager.CacheConfig config = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true, true, 300L, ConditionalExpire.class, false, 30L);
        cacheManager.registerCache("expire-load-test", config);

        // Load initial value - condition is false, so it caches
        ConditionalExpire.shouldExpire = false;
        String val1 = cacheManager.getOrLoad("expire-load-test", "key", String.class, () -> "fresh");
        assertThat(val1).isEqualTo("fresh");

        // Now set condition to expire
        ConditionalExpire.shouldExpire = true;

        // Next call should detect expiration, evict, and return null (not reload)
        String val2 = cacheManager.getOrLoad("expire-load-test", "key", String.class, () -> "should-not-be-called");
        assertThat(val2).isNull();

        // Verify it was evicted from both L1 and L2
        String raw = cacheManager.getRaw("expire-load-test", "key");
        assertThat(raw).isNull();
    }

    @Test
    void test_H2_getOrLoad_expireConditionL2Eviction_whenL2Enabled() {
        // Test that L2 eviction happens when expire condition triggers
        MultiLevelCacheManager.CacheConfig config = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true, true, 300L, ConditionalExpire.class, false, 30L);
        cacheManager.registerCache("expire-l2-evict-test", config);

        // Load and cache
        ConditionalExpire.shouldExpire = false;
        cacheManager.getOrLoad("expire-l2-evict-test", "key", String.class, () -> "value");

        // Trigger expiration
        ConditionalExpire.shouldExpire = true;
        cacheManager.getOrLoad("expire-l2-evict-test", "key", String.class, () -> "new");

        // Verify L2 was also evicted (not just L1)
        // Reset condition and try to load - should call origin again
        ConditionalExpire.shouldExpire = false;
        String result = cacheManager.getOrLoad("expire-l2-evict-test", "key", String.class, () -> "reloaded");
        assertThat(result).isEqualTo("reloaded");
    }

    // ========== M-1: NeverExpire direct test ==========

    @Test
    void test_M1_neverExpire_alwaysReturnsFalse() {
        NeverExpire neverExpire = new NeverExpire();
        boolean result = neverExpire.isExpired("any-cache", "any-key", "any-value");
        assertThat(result).isFalse();
    }
}
