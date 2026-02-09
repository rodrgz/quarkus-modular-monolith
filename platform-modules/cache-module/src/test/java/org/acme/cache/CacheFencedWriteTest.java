package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for putFenced stale token rejection (H-3).
 */
@QuarkusTest
public class CacheFencedWriteTest {

    @Inject
    MultiLevelCacheManager cacheManager;

    @Inject
    FencingTokenProvider fencingTokenProvider;

    @Test
    void test_H3_putFenced_staleToken_rejectsWrite() {
        cacheManager.registerCache("fenced-reject", MultiLevelCacheManager.CacheConfig.defaults());

        // Acquire two tokens - token2 is newer
        long token1 = fencingTokenProvider.nextToken("fenced-reject", "key");
        long token2 = fencingTokenProvider.nextToken("fenced-reject", "key");

        // Set the current token to token2
        boolean accepted2 = cacheManager.putFenced("fenced-reject", "key", "value-with-token2", token2);
        assertThat(accepted2).isTrue();

        // Try to write with stale token1 - should be rejected
        boolean rejected = cacheManager.putFenced("fenced-reject", "key", "value-with-stale-token", token1);
        assertThat(rejected).isFalse();

        // Verify the value is still the one written with token2
        String value = cacheManager.get("fenced-reject", "key", String.class);
        assertThat(value).isEqualTo("value-with-token2");
    }

    @Test
    void test_H3_putFenced_equalToken_accepts() {
        cacheManager.registerCache("fenced-equal", MultiLevelCacheManager.CacheConfig.defaults());

        long token = fencingTokenProvider.nextToken("fenced-equal", "key");

        // Write with the same token twice - both should succeed (>= check)
        boolean first = cacheManager.putFenced("fenced-equal", "key", "first", token);
        boolean second = cacheManager.putFenced("fenced-equal", "key", "second", token);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @Test
    void test_H3_putFenced_newerToken_accepts() {
        cacheManager.registerCache("fenced-newer", MultiLevelCacheManager.CacheConfig.defaults());

        long token1 = fencingTokenProvider.nextToken("fenced-newer", "key");
        long token3 = fencingTokenProvider.nextToken("fenced-newer", "key");

        // Write with token1
        cacheManager.putFenced("fenced-newer", "key", "v1", token1);

        // Write with newer token3 - should succeed
        boolean accepted = cacheManager.putFenced("fenced-newer", "key", "v3", token3);
        assertThat(accepted).isTrue();

        String value = cacheManager.get("fenced-newer", "key", String.class);
        assertThat(value).isEqualTo("v3");
    }
}
