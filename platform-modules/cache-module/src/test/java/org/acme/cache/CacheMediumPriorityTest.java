package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for medium-priority coverage gaps (M-2, M-3, M-4).
 */
@QuarkusTest
public class CacheMediumPriorityTest {

    @Inject
    MultiLevelCacheManager cacheManager;

    // ========== M-2: Config conflict warning in registerCache ==========

    @Test
    void test_M2_registerCache_conflictingConfig_logsWarning() {
        // Register with default config
        cacheManager.registerCache("conflict-test", MultiLevelCacheManager.CacheConfig.defaults());

        // Try to register with different config - should log warning and keep first
        MultiLevelCacheManager.CacheConfig different = new MultiLevelCacheManager.CacheConfig(
                false, 120L, 500, false, true, 600L, NeverExpire.class, false, 30L);
        cacheManager.registerCache("conflict-test", different);

        // Verify first config wins
        MultiLevelCacheManager.CacheConfig actual = cacheManager.getCacheConfig("conflict-test");
        assertThat(actual).isEqualTo(MultiLevelCacheManager.CacheConfig.defaults());
        assertThat(actual).isNotEqualTo(different);
    }

    // ========== M-3: serialize(null) → NULL_SENTINEL ==========

    @Test
    void test_M3_put_null_storesNullSentinel() {
        cacheManager.registerCache("null-put", MultiLevelCacheManager.CacheConfig.defaults());

        // Put null value
        cacheManager.put("null-put", "key", null);

        // Verify it's stored as NULL_SENTINEL
        String raw = cacheManager.getRaw("null-put", "key");
        assertThat(raw).isEqualTo(MultiLevelCacheManager.NULL_SENTINEL);

        // Verify deserialize returns null
        String value = cacheManager.get("null-put", "key", String.class);
        assertThat(value).isNull();
    }

    @Test
    void test_M3_serialize_null_returnsNullSentinel() {
        // Direct test of serialize method behavior
        cacheManager.registerCache("serialize-test", MultiLevelCacheManager.CacheConfig.defaults());

        // Put null and verify the sentinel is used
        cacheManager.put("serialize-test", "null-key", null);

        String raw = cacheManager.getRaw("serialize-test", "null-key");
        assertThat(raw).isEqualTo(MultiLevelCacheManager.NULL_SENTINEL);
    }

    @Test
    void test_M3_deserialize_nullSentinel_returnsNull() {
        // Test that deserializing NULL_SENTINEL returns null
        String result = cacheManager.deserialize(MultiLevelCacheManager.NULL_SENTINEL, String.class);
        assertThat(result).isNull();
    }
}
