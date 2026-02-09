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
 * <p>Wraps scheduled method execution with distributed lock acquisition.
 * If lock cannot be acquired (another instance holds it), the method
 * execution is skipped silently.</p>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 *   <li>Tries to acquire lock before method execution</li>
 *   <li>If lock acquired: executes method, then releases lock</li>
 *   <li>If lock not acquired: skips execution, returns null</li>
 *   <li>Lock is always released even if method throws exception</li>
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
        
        LOG.debugf("Attempting to acquire lock: %s", lockName);
        
        var lockHandle = lockProvider.tryLock(lockName, lockDuration);
        
        if (lockHandle.isEmpty()) {
            LOG.infof("Skipping execution of %s.%s - lock '%s' held by another instance",
                context.getTarget().getClass().getSimpleName(),
                context.getMethod().getName(),
                lockName);
            return null;
        }
        
        try (var lock = lockHandle.get()) {
            LOG.infof("Executing %s.%s with lock '%s'",
                context.getTarget().getClass().getSimpleName(),
                context.getMethod().getName(),
                lockName);
            return context.proceed();
        }
    }
}
