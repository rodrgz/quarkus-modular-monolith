package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@QuarkusTest
public class ConcurrencyTest {

    @Inject
    ConcurrentService service;

    @BeforeEach
    void setup() {
        // Using real Redis Dev Services
        ConcurrentService.callCount.set(0);
    }

    @Test
    public void test_GL04_thunderingHerd_multipleThreads_singleOriginCall() throws Exception {
        int threadCount = 10;
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Start 10 threads requesting the same key
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> service.getSlowData("shared-key")));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify results
        for (CompletableFuture<String> future : futures) {
            Assertions.assertEquals("slow-data-shared-key", future.get());
        }

        // CRITICAL: origin should have been called exactly once
        Assertions.assertEquals(1, ConcurrentService.callCount.get(),
                "Origin should be called once for all concurrent threads (GL-04)");
    }

    @ApplicationScoped
    public static class ConcurrentService {
        static AtomicInteger callCount = new AtomicInteger(0);

        @MultiLevelCache(cacheName = "concurrent-cache")
        public String getSlowData(String key) {
            callCount.incrementAndGet();
            try {
                // Simulate slow origin call to increase chance of overlap
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "slow-data-" + key;
        }
    }
}
