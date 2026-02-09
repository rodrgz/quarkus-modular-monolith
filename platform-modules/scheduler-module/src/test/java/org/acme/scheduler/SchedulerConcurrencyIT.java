package org.acme.scheduler;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for distributed scheduler scenarios.
 * Covers scenarios: SC-03 to SC-10.
 */
@QuarkusTest
public class SchedulerConcurrencyIT {

    @Inject
    LockProvider lockProvider;

    // ========== SC-03: Pod crash + SETNX sem EXPIRE ==========
    @Test
    public void test_SC03_podCrash_noImmortalLock() throws InterruptedException {
        // This is FIXED in RedisLockProvider using Lua script
        // Verify lock expires even if "crash" happens
        Optional<LockProvider.LockHandle> handle = lockProvider.tryLock("crash-test-sc03", Duration.ofSeconds(2));
        assertTrue(handle.isPresent());

        // Simulate crash (don't close)
        Thread.sleep(2500);

        // Lock should be available
        Optional<LockProvider.LockHandle> handle2 = lockProvider.tryLock("crash-test-sc03", Duration.ofSeconds(60));
        assertTrue(handle2.isPresent(), "Lock not immortal after crash (SC-03 FIXED)");
        handle2.get().close();
    }

    // ========== SC-04: GC pause > lockAtMostFor ==========
    @Test
    public void test_SC04_gcPause_lockStolen() throws InterruptedException {
        Optional<LockProvider.LockHandle> handleA = lockProvider.tryLock("gc-pause-sc04", Duration.ofSeconds(2));
        assertTrue(handleA.isPresent());

        // Simulate GC pause (sleep)
        Thread.sleep(2500);

        // Pod B acquires lock
        Optional<LockProvider.LockHandle> handleB = lockProvider.tryLock("gc-pause-sc04", Duration.ofSeconds(60));
        assertTrue(handleB.isPresent());

        // Pod A wakes up and tries to release (no longer owner)
        assertDoesNotThrow(() -> handleA.get().close(), "Release of stolen lock is safe (SC-04)");

        handleB.get().close();
    }

    // ========== SC-05: Network partition no release ==========
    @Test
    public void test_SC05_networkPartition_lockExpires() throws InterruptedException {
        Optional<LockProvider.LockHandle> handle = lockProvider.tryLock("network-fail-sc05", Duration.ofSeconds(2));
        assertTrue(handle.isPresent());

        // Simulate network partition (can't release)
        // Lock should expire by TTL
        Thread.sleep(2500);

        // Another pod can acquire
        Optional<LockProvider.LockHandle> handle2 = lockProvider.tryLock("network-fail-sc05", Duration.ofSeconds(60));
        assertTrue(handle2.isPresent(), "Lock expires despite network partition (SC-05)");
        handle2.get().close();
    }

    // ========== SC-06: Extend durante execução longa ==========
    @Test
    public void test_SC06_longExecution_noAutoExtend() throws InterruptedException {
        Optional<LockProvider.LockHandle> handle = lockProvider.tryLock("long-job-sc06", Duration.ofSeconds(2));
        assertTrue(handle.isPresent());

        // Simulate long job (no auto-extend)
        Thread.sleep(2500);

        // Lock expired - another pod can acquire
        Optional<LockProvider.LockHandle> handle2 = lockProvider.tryLock("long-job-sc06", Duration.ofSeconds(60));
        assertTrue(handle2.isPresent(), "Lock expires during long execution (SC-06 WARNING)");

        handle.get().close(); // Original handle
        handle2.get().close();
    }

    // ========== SC-07: Duplicate execution detection ==========
    @Test
    public void test_SC07_duplicateExecution_noPrevention() throws InterruptedException {
        // This scenario documents that there's NO built-in duplicate detection
        // If two pods execute the same job, results may duplicate
        AtomicInteger executionCount = new AtomicInteger(0);

        // Pod 1 acquires lock
        Optional<LockProvider.LockHandle> handle1 = lockProvider.tryLock("dup-job-sc07", Duration.ofSeconds(1));
        assertTrue(handle1.isPresent());
        executionCount.incrementAndGet();

        // Wait for expiration
        Thread.sleep(1500);

        // Pod 2 acquires lock (duplicate execution)
        Optional<LockProvider.LockHandle> handle2 = lockProvider.tryLock("dup-job-sc07", Duration.ofSeconds(60));
        assertTrue(handle2.isPresent());
        executionCount.incrementAndGet();

        assertEquals(2, executionCount.get(), "No duplicate execution prevention (SC-07)");
        handle2.get().close();
    }

    // ========== SC-08: Lock name collision ==========
    @Test
    public void test_SC08_lockNameCollision_mutualExclusion() {
        // Two different jobs with same lock name
        Optional<LockProvider.LockHandle> job1 = lockProvider.tryLock("shared-lock-sc08", Duration.ofSeconds(60));
        assertTrue(job1.isPresent());

        Optional<LockProvider.LockHandle> job2 = lockProvider.tryLock("shared-lock-sc08", Duration.ofSeconds(60));
        assertTrue(job2.isEmpty(), "Lock name collision causes mutual exclusion (SC-08)");

        job1.get().close();
    }

    // ========== SC-09: Redis failover ==========
    // Requires Redis cluster setup - documented as limitation

    // ========== SC-10: lockAtLeastFor com exceção ==========
    @Test
    public void test_SC10_lockAtLeastFor_withException() {
        // When method throws, try-with-resources closes lock immediately
        // lockAtLeastFor sleep is NOT executed
        long start = System.currentTimeMillis();

        try (var handle = lockProvider.tryLock("exception-job-sc10", Duration.ofSeconds(60)).orElseThrow()) {
            // Simulate exception after quick work
            try {
                Thread.sleep(10);
                throw new RuntimeException("Job failed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (RuntimeException e) {
            // Expected
        }

        long duration = System.currentTimeMillis() - start;

        // Lock released immediately (no lockAtLeastFor sleep on exception)
        assertTrue(duration < 1000, "Lock released immediately on exception (SC-10). Actual: " + duration + "ms");

        // Verify lock is available
        Optional<LockProvider.LockHandle> handle2 = lockProvider.tryLock("exception-job-sc10", Duration.ofSeconds(60));
        assertTrue(handle2.isPresent(), "Lock available immediately after exception");
        handle2.get().close();
    }

    // ========== Additional concurrency test: Multiple threads ==========
    @Test
    public void test_multipleThreads_onlyOneAcquires() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    Optional<LockProvider.LockHandle> handle = lockProvider.tryLock("multi-thread-test",
                            Duration.ofSeconds(60));
                    if (handle.isPresent()) {
                        successCount.incrementAndGet();
                        Thread.sleep(50);
                        handle.get().close();
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

        assertEquals(1, successCount.get(), "Only one thread acquires lock");
    }
}
