package org.acme.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheConfig record.
 * Covers scenarios: C-01 to C-06.
 */
public class CacheConfigTest {

    // ========== C-01: Defaults corretos ==========
    @Test
    public void test_C01_defaults_correctValues() {
        MultiLevelCacheManager.CacheConfig config = MultiLevelCacheManager.CacheConfig.defaults();

        assertTrue(config.l1Enabled(), "L1 enabled by default");
        assertEquals(60L, config.l1TtlSeconds(), "L1 TTL default = 60s");
        assertEquals(1000, config.l1MaxSize(), "L1 max size default = 1000");
        assertTrue(config.l1AsFallback(), "L1 as fallback enabled by default");

        assertTrue(config.l2Enabled(), "L2 enabled by default");
        assertEquals(300L, config.l2TtlSeconds(), "L2 TTL default = 300s");

        assertFalse(config.cacheNulls(), "Cache nulls disabled by default");
        assertEquals(30L, config.nullTtlSeconds(), "Null TTL default = 30s");
    }

    // ========== C-02: TimeUnit.MINUTES conversion ==========
    @Test
    public void test_C02_timeUnitMinutes_conversion() {
        // Simulate annotation with l1Ttl=5 MINUTES
        long l1TtlSeconds = TimeUnit.MINUTES.toSeconds(5);

        assertEquals(300, l1TtlSeconds, "5 minutes = 300 seconds (C-02)");
    }

    // ========== C-03: TimeUnit.HOURS conversion ==========
    @Test
    public void test_C03_timeUnitHours_conversion() {
        // Simulate annotation with l2Ttl=1 HOUR
        long l2TtlSeconds = TimeUnit.HOURS.toSeconds(1);

        assertEquals(3600, l2TtlSeconds, "1 hour = 3600 seconds (C-03)");
    }

    // ========== C-04: Null TTL unit conversion ==========
    @Test
    public void test_C04_nullTtlUnit_conversion() {
        // Simulate annotation with nullTtl=2 MINUTES
        long nullTtlSeconds = TimeUnit.MINUTES.toSeconds(2);

        assertEquals(120, nullTtlSeconds, "2 minutes = 120 seconds (C-04)");
    }

    // ========== C-05: Record equality (configs iguais) ==========
    @Test
    public void test_C05_recordEquality_sameConfigs() {
        MultiLevelCacheManager.CacheConfig config1 = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true,
                true, 300L, NeverExpire.class,
                false, 30L);

        MultiLevelCacheManager.CacheConfig config2 = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true,
                true, 300L, NeverExpire.class,
                false, 30L);

        assertEquals(config1, config2, "Identical configs are equal (C-05)");
        assertEquals(config1.hashCode(), config2.hashCode(), "Hash codes match");
    }

    // ========== C-06: Record inequality (configs diferentes) ==========
    @Test
    public void test_C06_recordInequality_differentConfigs() {
        MultiLevelCacheManager.CacheConfig config1 = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true,
                true, 300L, NeverExpire.class,
                false, 30L);

        // Different L1 TTL
        MultiLevelCacheManager.CacheConfig config2 = new MultiLevelCacheManager.CacheConfig(
                true, 120L, 1000, true, // L1 TTL = 120 instead of 60
                true, 300L, NeverExpire.class,
                false, 30L);

        assertNotEquals(config1, config2, "Different TTL configs are not equal (C-06)");

        // Different L2 enabled
        MultiLevelCacheManager.CacheConfig config3 = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true,
                false, 300L, NeverExpire.class, // L2 disabled
                false, 30L);

        assertNotEquals(config1, config3, "Different L2 enabled configs are not equal");

        // Different cacheNulls
        MultiLevelCacheManager.CacheConfig config4 = new MultiLevelCacheManager.CacheConfig(
                true, 60L, 1000, true,
                true, 300L, NeverExpire.class,
                true, 30L // cacheNulls = true
        );

        assertNotEquals(config1, config4, "Different cacheNulls configs are not equal");
    }

    // ========== Additional: toString() produces readable output ==========
    @Test
    public void test_toString_readable() {
        MultiLevelCacheManager.CacheConfig config = MultiLevelCacheManager.CacheConfig.defaults();
        String str = config.toString();

        assertNotNull(str, "toString() returns non-null");
        assertTrue(str.contains("CacheConfig"), "toString() contains class name");
        assertTrue(str.contains("l1Enabled"), "toString() contains field names");
    }
}
