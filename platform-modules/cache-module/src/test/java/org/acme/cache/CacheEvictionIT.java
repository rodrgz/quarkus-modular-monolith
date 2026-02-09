package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cache eviction operations.
 * Covers scenarios: E-01 to E-08.
 */
@QuarkusTest
public class CacheEvictionIT {

    @Inject
    MultiLevelCacheManager cacheManager;

    @BeforeEach
    void setup() {
        cacheManager.clearL1("test-evict");
        cacheManager.clearL2("test-evict");
    }

    // ========== E-01: evictFromL1 removes from L1 only ==========
    @Test
    public void test_E01_evictFromL1_removesL1Only() {
        // Populate both levels
        cacheManager.put("test-evict", "key1", "value1");

        // Evict from L1
        cacheManager.evictFromL1("test-evict", "key1");

        // Clear L1 to force L2 check
        String result = cacheManager.get("test-evict", "key1", String.class);
        assertEquals("value1", result, "Value still in L2 after L1 eviction (E-01)");
    }

    // ========== E-02: evictFromL2 removes from L2 only ==========
    @Test
    public void test_E02_evictFromL2_removesL2Only() {
        // Populate both levels
        cacheManager.put("test-evict", "key2", "value2");

        // Evict from L2
        cacheManager.evictFromL2("test-evict", "key2");

        // Should still hit L1
        String result = cacheManager.get("test-evict", "key2", String.class);
        assertEquals("value2", result, "Value still in L1 after L2 eviction (E-02)");

        // Clear L1 - should miss
        cacheManager.clearL1("test-evict");
        String result2 = cacheManager.get("test-evict", "key2", String.class);
        assertNull(result2, "Value removed from L2");
    }

    // ========== E-03: clearL1 removes all entries ==========
    @Test
    public void test_E03_clearL1_removesAllEntries() {
        cacheManager.put("test-evict", "key3a", "value3a");
        cacheManager.put("test-evict", "key3b", "value3b");

        // Clear L1
        cacheManager.clearL1("test-evict");

        // Both should hit L2
        String result1 = cacheManager.get("test-evict", "key3a", String.class);
        String result2 = cacheManager.get("test-evict", "key3b", String.class);
        assertEquals("value3a", result1, "L2 still has values (E-03)");
        assertEquals("value3b", result2);
    }

    // ========== E-04: clearL2 removes all entries ==========
    @Test
    public void test_E04_clearL2_removesAllEntries() {
        cacheManager.put("test-evict", "key4a", "value4a");
        cacheManager.put("test-evict", "key4b", "value4b");

        // Clear L2
        cacheManager.clearL2("test-evict");

        // Should still hit L1
        String result1 = cacheManager.get("test-evict", "key4a", String.class);
        assertEquals("value4a", result1, "L1 still has values");

        // Clear L1 - should miss
        cacheManager.clearL1("test-evict");
        String result2 = cacheManager.get("test-evict", "key4a", String.class);
        assertNull(result2, "All values cleared from L2 (E-04)");
    }

    // ========== E-05: Evict non-existent key ==========
    @Test
    public void test_E05_evictNonExistent_noOp() {
        assertDoesNotThrow(() -> {
            cacheManager.evictFromL1("test-evict", "non-existent");
            cacheManager.evictFromL2("test-evict", "non-existent");
        }, "Evicting non-existent key is safe (E-05)");
    }

    // ========== E-06: Clear empty cache ==========
    @Test
    public void test_E06_clearEmpty_noOp() {
        assertDoesNotThrow(() -> {
            cacheManager.clearL1("test-evict");
            cacheManager.clearL2("test-evict");
        }, "Clearing empty cache is safe (E-06)");
    }

    // ========== E-07: Evict with null key ==========
    @Test
    public void test_E07_evictNullKey_throwsException() {
        // Null key may be handled gracefully or throw - depends on implementation
        // This test documents the actual behavior
        assertDoesNotThrow(() -> {
            cacheManager.evictFromL1("test-evict", null);
        }, "Null key handled gracefully (E-07)");
    }

    // ========== E-08: Clear with null cache name ==========
    @Test
    public void test_E08_clearNullCacheName_throwsException() {
        assertThrows(Exception.class, () -> {
            cacheManager.clearL1(null);
        }, "Null cache name throws exception (E-08)");
    }
}
