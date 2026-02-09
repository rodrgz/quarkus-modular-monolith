package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

@QuarkusTest
public class CacheInvalidationDeepTest {

    @Inject
    InvalidationService service;

    @Inject
    CacheInvalidator invoker;

    @BeforeEach
    void setup() {
        // Using real Redis Dev Services
        InvalidationService.callCount.set(0);
    }

    @Test
    public void test_CI06_invalidateByMethod_successAfterFix() {
        // 1. Cache a value with complex params
        service.getData("abc", 123);
        Assertions.assertEquals(1, InvalidationService.callCount.get());

        // 2. Invalidate it using the same params via the FIXED invoker method
        invoker.invalidateByMethod("deep-cache", "getData", "abc", 123);

        // 3. Try to get it again - if invalidation worked, callCount should be 2
        service.getData("abc", 123);

        int countAfterInvalidate = InvalidationService.callCount.get();
        System.out.println("Call count after invalidate: " + countAfterInvalidate);

        Assertions.assertEquals(2, countAfterInvalidate,
                "FIX VERIFIED: invalidateByMethod correctly invalidated the entry!");
    }

    @ApplicationScoped
    public static class InvalidationService {
        static AtomicInteger callCount = new AtomicInteger(0);

        @MultiLevelCache(cacheName = "deep-cache")
        public String getData(String p1, Integer p2) {
            callCount.incrementAndGet();
            return "data-" + p1 + "-" + p2;
        }
    }
}
