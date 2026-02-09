package org.acme.scheduler;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Interceptor for @DistributedLock annotation.
 * 
 * <p>
 * Wraps scheduled method execution with distributed lock acquisition.
 * If lock cannot be acquired (another instance holds it), the method
 * execution is skipped silently.
 * </p>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 * <li>Tries to acquire lock before method execution</li>
 * <li>If lock acquired: executes method, then releases lock</li>
 * <li>If lock not acquired: skips execution, returns null</li>
 * <li>Lock is always released even if method throws exception</li>
 * </ul>
 */
@DistributedLock(name = "")
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class DistributedLockInterceptor {

    private static final Logger LOG = Logger.getLogger(DistributedLockInterceptor.class);

    @Inject
    LockProvider lockProvider;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        DistributedLock annotation = context.getMethod().getAnnotation(DistributedLock.class);

        if (annotation == null) {
            return context.proceed();
        }

        String lockName = annotation.name();
        Duration lockDuration = Duration.ofSeconds(annotation.lockAtMostFor());
        long lockAtLeastForMs = annotation.lockAtLeastFor() * 1000;

        if (lockDuration.toMillis() < lockAtLeastForMs) {
            LOG.warnf("Configuration risk: lockAtMostFor (%ds) is less than lockAtLeastFor (%ds) for lock '%s'. " +
                    "Lock will expire in Redis before the minimum hold time is reached.",
                    annotation.lockAtMostFor(), annotation.lockAtLeastFor(), lockName);
        }

        LOG.debugf("Attempting to acquire lock: %s", lockName);

        long startTime = System.currentTimeMillis();
        var lockHandle = lockProvider.tryLock(lockName, lockDuration);

        if (lockHandle.isEmpty()) {
            LOG.infof("Skipping execution of %s.%s - lock '%s' held by another instance",
                    context.getTarget().getClass().getSimpleName(),
                    context.getMethod().getName(),
                    lockName);
            return null;
        }

        LockProvider.LockHandle lock = lockHandle.get();
        try {
            LOG.infof("Executing %s.%s with lock '%s'",
                    context.getTarget().getClass().getSimpleName(),
                    context.getMethod().getName(),
                    lockName);
            return context.proceed();
        } finally {
            try {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < lockAtLeastForMs) {
                    long sleepTime = lockAtLeastForMs - elapsed;
                    LOG.debugf("Method finished in %dms, sleeping %dms to honor lockAtLeastFor",
                            elapsed, sleepTime);
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.close();
            }
        }
    }
}
