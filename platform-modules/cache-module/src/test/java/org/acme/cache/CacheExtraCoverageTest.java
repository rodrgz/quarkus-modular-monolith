package org.acme.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests to cover specific blocks identified as uncovered:
 * 1. Redis Pub/Sub subscription in background thread
 * 2. L1 population from L2 in get()
 * 3. Invalidation logic for messages from other instances
 */
@QuarkusTest
public class CacheExtraCoverageTest {

    @Inject
    MultiLevelCacheManager cacheManager;

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    // ========== H-6: L1 population from L2 in get() ==========

    @Test
    void test_H6_get_populatesL1FromL2() {
        String cacheName = "h6-test";
        String key = "key1";
        String value = "value1";

        // 1. Manually put value in L2 ONLY
        MultiLevelCacheManager.CacheConfig config = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true, true, 300L, NeverExpire.class, false, 30L);
        cacheManager.registerCache(cacheName, config);

        // Put in L2 manually (directly via Redis or via Manager)
        cacheManager.put(cacheName, key, value);

        // Clear L1 to force L2 hit
        cacheManager.clearL1(cacheName);
        assertThat(cacheManager.getRawL1(cacheName, key)).isNull();

        // 2. Call get() - this should hit L2 and populate L1 (Block 2)
        String retrieved = cacheManager.get(cacheName, key, String.class);
        assertThat(retrieved).isEqualTo(value);

        // 3. Verify L1 is now populated
        String l1Raw = cacheManager.getRawL1(cacheName, key);
        assertThat(l1Raw).isNotNull();
    }

    // ========== H-7: Handle invalidation from external instance ==========

    @Test
    void test_H7_handleInvalidationFromExternalInstance() throws Exception {
        String cacheName = "h7-test";
        String key = "key1";

        cacheManager.registerCache(cacheName, MultiLevelCacheManager.CacheConfig.defaults());
        cacheManager.put(cacheName, key, "value");
        assertThat(cacheManager.getRawL1(cacheName, key)).isNotNull();

        // 1. Publish a message with a DIFFERENT instanceId
        String otherInstanceId = UUID.randomUUID().toString();
        // Construct JSON manually to match InvalidationMessage record
        String json = String.format("{\"cacheName\":\"%s\",\"key\":\"%s\",\"sourceInstanceId\":\"%s\"}",
                cacheName, key, otherInstanceId);

        redisDataSource.pubsub(String.class).publish(cacheManager.getInvalidationChannel(), json);

        // 2. Wait for async delivery and verify L1 is evicted (Block 3)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(cacheManager.getRawL1(cacheName, key)).isNull();
        });
    }

    @Test
    void test_H7_handleWildcardInvalidation() throws Exception {
        String cacheName = "h7-wildcard-test";
        cacheManager.registerCache(cacheName, MultiLevelCacheManager.CacheConfig.defaults());
        cacheManager.put(cacheName, "k1", "v1");
        cacheManager.put(cacheName, "k2", "v2");

        // 1. Publish wildcard message from other instance
        String otherInstanceId = UUID.randomUUID().toString();
        String json = String.format("{\"cacheName\":\"%s\",\"key\":\"*\",\"sourceInstanceId\":\"%s\"}",
                cacheName, otherInstanceId);

        redisDataSource.pubsub(String.class).publish(cacheManager.getInvalidationChannel(), json);

        // 2. Verify all keys cleared in L1
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(cacheManager.getRawL1(cacheName, "k1")).isNull();
            assertThat(cacheManager.getRawL1(cacheName, "k2")).isNull();
        });
    }

    // ========== H-8: L1 disabled coalescing (L532-533) ==========

    @Test
    void test_H8_l1Disabled_usesShortTtlForCoalescing() {
        String cacheName = "l1-disabled-test";
        MultiLevelCacheManager.CacheConfig config = new MultiLevelCacheManager.CacheConfig(
                false, 60L, 1000, true, true, 300L, NeverExpire.class, false, 30L);
        cacheManager.registerCache(cacheName, config);

        // This triggers the computeIfAbsent branch where l1Enabled is false
        // and sets TTL to 1s
        cacheManager.getOrLoad(cacheName, "key", String.class, () -> "val");

        // Verify it works (the branch coverage is in getL1Cache)
        assertThat(cacheManager.getRawL1(cacheName, "key")).isNotNull();
    }
}
