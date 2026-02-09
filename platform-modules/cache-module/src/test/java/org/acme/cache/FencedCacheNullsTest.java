package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

@QuarkusTest
public class FencedCacheNullsTest {

    @Inject
    FencedNullService service;

    @BeforeEach
    void setup() {
        // Using real Redis Dev Services
        FencedNullService.callCount.set(0);
    }

    @Test
    public void test_CC03_fenced_cacheNulls_true() {
        // First call returns null
        String val1 = service.getNullFenced("k1");
        Assertions.assertNull(val1);
        Assertions.assertEquals(1, FencedNullService.callCount.get());

        // Second call should HIT cache (null sentinel) and NOT call origin
        String val2 = service.getNullFenced("k1");
        Assertions.assertNull(val2);
        Assertions.assertEquals(1, FencedNullService.callCount.get(),
                "Fenced mode should respect cacheNulls=true (CC-03 FIXED)");
    }

    @Test
    public void test_CC03_fenced_cacheNulls_false() {
        // First call returns null
        String val1 = service.getNullFencedNoCache("k2");
        Assertions.assertNull(val1);
        Assertions.assertEquals(1, FencedNullService.callCount.get());

        // Second call should MISS cache (since cacheNulls=false) and call origin again
        String val2 = service.getNullFencedNoCache("k2");
        Assertions.assertNull(val2);
        Assertions.assertEquals(2, FencedNullService.callCount.get(),
                "Fenced mode should respect cacheNulls=false");
    }

    @ApplicationScoped
    public static class FencedNullService {
        static AtomicInteger callCount = new AtomicInteger(0);

        @MultiLevelCache(cacheName = "fenced-null-cache", fenced = true, cacheNulls = true)
        public String getNullFenced(String key) {
            callCount.incrementAndGet();
            return null;
        }

        @MultiLevelCache(cacheName = "fenced-no-null-cache", fenced = true, cacheNulls = false)
        public String getNullFencedNoCache(String key) {
            callCount.incrementAndGet();
            return null;
        }
    }
}
