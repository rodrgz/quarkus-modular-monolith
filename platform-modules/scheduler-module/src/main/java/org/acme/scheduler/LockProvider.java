package org.acme.scheduler;

import java.time.Duration;
import java.util.Optional;

/**
 * Interface for distributed lock providers.
 * 
 * Implementations can use Redis, database, or other mechanisms
 * to provide distributed locking across multiple instances.
 */
public interface LockProvider {
    
    /**
     * Attempts to acquire a lock with the given name.
     * 
     * @param name The lock name (unique identifier)
     * @param lockAtMostFor Maximum duration to hold the lock
     * @return LockHandle if lock acquired, empty if lock already held
     */
    Optional<LockHandle> tryLock(String name, Duration lockAtMostFor);
    
    /**
     * Handle representing an acquired lock.
     * Must be closed to release the lock.
     */
    interface LockHandle extends AutoCloseable {
        
        /**
         * The name of the lock.
         */
        String name();
        
        /**
         * Extends the lock duration.
         * 
         * @param duration Additional duration to hold
         * @return true if extension succeeded
         */
        boolean extend(Duration duration);
        
        /**
         * Releases the lock immediately.
         */
        @Override
        void close();
    }
}
