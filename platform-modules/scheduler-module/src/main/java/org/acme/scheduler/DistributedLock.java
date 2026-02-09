package org.acme.scheduler;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for distributed lock on scheduled methods.
 * 
 * Ensures that a scheduled task runs on only ONE pod in a Kubernetes cluster.
 * Uses Redis for distributed locking with SETNX pattern.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
 * @DistributedLock(name = "daily-report", lockAtMostFor = 3600)
 * public void generateDailyReport() {
 *     // Only executes on one pod
 * }
 * }</pre>
 * 
 * @see DistributedLockInterceptor
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@InterceptorBinding
public @interface DistributedLock {
    
    /**
     * Unique name for the lock. Must be unique across the application.
     * This becomes the Redis key: "lock:{name}"
     */
    @Nonbinding
    String name();
    
    /**
     * Maximum time to hold the lock in seconds.
     * This prevents stale locks if a pod crashes without releasing.
     * Default: 5 minutes (300 seconds)
     */
    @Nonbinding
    long lockAtMostFor() default 300;
    
    /**
     * Minimum time to hold the lock in seconds.
     * Prevents another execution from starting too quickly.
     * Default: 1 minute (60 seconds)
     */
    @Nonbinding
    long lockAtLeastFor() default 60;
}
