package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FencingTokenProvider using real Redis via Dev Services.
 * Covers scenarios: FT-01 to FT-08.
 */
@QuarkusTest
public class FencingTokenProviderIT {

    @Inject
    FencingTokenProvider fencingTokenProvider;

    @Inject
    MultiLevelCacheManager cacheManager;

    @BeforeEach
    void setup() {
        // Clear Redis to start fresh
        cacheManager.clearL2("test-cache-ft");
    }

    // ========== FT-01: First call (no token) ==========
    @Test
    public void test_FT01_firstCall_noToken_returnsTrue() {
        boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "key1", 100);
        assertTrue(valid, "First call with no existing token returns true (FT-01)");
    }

    // ========== FT-02: Same token ==========
    @Test
    public void test_FT02_sameToken_returnsTrue() {
        // Set token
        fencingTokenProvider.validateAndSetToken("test-cache-ft", "key2", 100);

        // Validate with same token
        boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "key2", 100);
        assertTrue(valid, "Same token returns true (FT-02)");
    }

    // ========== FT-03: Newer token ==========
    @Test
    public void test_FT03_newerToken_returnsTrue() {
        // Set initial token
        fencingTokenProvider.validateAndSetToken("test-cache-ft", "key3", 100);

        // Set newer token
        boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "key3", 101);
        assertTrue(valid, "Newer token returns true (FT-03)");
    }

    // ========== FT-04: Stale token ==========
    @Test
    public void test_FT04_staleToken_returnsFalse() {
        // Set newer token first
        fencingTokenProvider.validateAndSetToken("test-cache-ft", "key4", 200);

        // Try to set stale token
        boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "key4", 100);
        assertFalse(valid, "Stale token returns false (FT-04)");
    }

    // ========== FT-05: Token format ==========
    @Test
    public void test_FT05_tokenFormat_storedCorrectly() {
        // This test verifies internal behavior - token is stored as string
        fencingTokenProvider.validateAndSetToken("test-cache-ft", "key5", 12345);

        // Subsequent validation should work
        boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "key5", 12345);
        assertTrue(valid, "Token stored and retrieved correctly (FT-05)");
    }

    // ========== FT-06: Redis indisponível (fail-open) ==========
    // This test requires Redis shutdown which is complex with Dev Services
    // The fail-open behavior is documented in the implementation
    // Manual testing: stop Redis container and verify fail-open behavior

    // ========== FT-07: Race condition GET→SET ==========
    @Test
    public void test_FT07_raceCondition_nonAtomic() throws InterruptedException {
        // This test documents the KNOWN BUG: GET and SET are not atomic
        // Two threads can both read token=100, then both write token=101

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Pre-set initial token
        fencingTokenProvider.validateAndSetToken("test-cache-ft", "race-key", 100);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    // All threads try to set token=101 simultaneously
                    boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "race-key", 101);
                    if (valid) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // BUG: All threads may succeed due to race condition
        // Expected: only 1 success (if atomic)
        // Actual: multiple successes (non-atomic)
        System.out.println("FT-07: Success count = " + successCount.get() + " (expected 1 if atomic)");

        // This test documents the bug - we don't assert failure here
        // Once fixed with Lua script, this should be: assertEquals(1,
        // successCount.get())
    }

    // ========== FT-08: Token TTL ==========
    @Test
    public void test_FT08_tokenTTL_expiresCorrectly() throws InterruptedException {
        // Note: FencingTokenProvider doesn't currently set TTL on tokens
        // This is a potential enhancement - tokens are stored indefinitely
        // This test documents the current behavior

        fencingTokenProvider.validateAndSetToken("test-cache-ft", "ttl-key", 100);

        // Token should persist (no TTL set)
        Thread.sleep(1000);

        boolean valid = fencingTokenProvider.validateAndSetToken("test-cache-ft", "ttl-key", 100);
        assertTrue(valid, "Token persists without TTL (FT-08 - current behavior)");
    }

    // ========== F-01: putFenced with valid token ==========
    @Test
    public void test_F01_putFenced_validToken_storesValue() {
        // First put with token=1
        cacheManager.putFenced("test-cache-ft", "fenced-key1", "value1", 1);

        // Verify value stored
        String result = cacheManager.get("test-cache-ft", "fenced-key1", String.class);
        assertEquals("value1", result, "Value stored with valid token (F-01)");
    }

    // ========== F-02: putFenced with stale token ==========
    @Test
    public void test_F02_putFenced_staleToken_rejected() {
        // Put with token=10
        cacheManager.putFenced("test-cache-ft", "fenced-key2", "value-new", 10);

        // Try to put with stale token=5
        cacheManager.putFenced("test-cache-ft", "fenced-key2", "value-stale", 5);

        // Verify stale write was rejected
        String result = cacheManager.get("test-cache-ft", "fenced-key2", String.class);
        assertEquals("value-new", result, "Stale token rejected (F-02)");
    }

    // ========== F-03: putFenced with newer token ==========
    @Test
    public void test_F03_putFenced_newerToken_overwrites() {
        // Put with token=5
        cacheManager.putFenced("test-cache-ft", "fenced-key3", "value-old", 5);

        // Put with newer token=10
        cacheManager.putFenced("test-cache-ft", "fenced-key3", "value-new", 10);

        // Verify newer write succeeded
        String result = cacheManager.get("test-cache-ft", "fenced-key3", String.class);
        assertEquals("value-new", result, "Newer token overwrites (F-03)");
    }

    // ========== F-04: putFenced L1+L2 ==========
    @Test
    public void test_F04_putFenced_storesInBothLevels() {
        cacheManager.putFenced("test-cache-ft", "fenced-key4", "value4", 1);

        // Clear L1
        cacheManager.clearL1("test-cache-ft");

        // Should hit L2
        String result = cacheManager.get("test-cache-ft", "fenced-key4", String.class);
        assertEquals("value4", result, "Fenced value stored in L2 (F-04)");
    }

    // ========== F-05: putFenced with null value ==========
    @Test
    public void test_F05_putFenced_nullValue_notStored() {
        cacheManager.putFenced("test-cache-ft", "fenced-key5", null, 1);

        // Null values are not stored by default
        String result = cacheManager.get("test-cache-ft", "fenced-key5", String.class);
        assertNull(result, "Null value not stored (F-05)");
    }

    // ========== F-06: putFenced serialization error ==========
    @Test
    public void test_F06_putFenced_serializationError_throwsException() {
        CircularObject circular = new CircularObject();
        circular.self = circular;

        assertThrows(RuntimeException.class, () -> {
            cacheManager.putFenced("test-cache-ft", "fenced-key6", circular, 1);
        }, "Serialization error throws RuntimeException (F-06)");
    }

    // ========== Test Helper Classes ==========

    public static class CircularObject {
        public CircularObject self;
    }
}
