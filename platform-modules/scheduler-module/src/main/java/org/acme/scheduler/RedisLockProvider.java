package org.acme.scheduler;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-based distributed lock provider using SETNX pattern.
 * 
 * <p>Uses Redis SET with NX (only set if not exists) and EX (expiration)
 * to achieve atomic lock acquisition with automatic expiration.</p>
 * 
 * <h2>Lock Key Format:</h2>
 * <pre>lock:{name}</pre>
 * 
 * <h2>Lock Value:</h2>
 * <p>Contains the instance ID to ensure only the owner can release the lock.</p>
 */
@ApplicationScoped
public class RedisLockProvider implements LockProvider {
    
    private static final Logger LOG = Logger.getLogger(RedisLockProvider.class);
    private static final String LOCK_PREFIX = "lock:";
    
    private final ValueCommands<String, String> redis;
    private final String instanceId;
    
    @Inject
    public RedisLockProvider(
            RedisDataSource redisDataSource,
            @ConfigProperty(name = "scheduler.instance-id", defaultValue = "") String configuredInstanceId
    ) {
        this.redis = redisDataSource.value(String.class);
        this.instanceId = configuredInstanceId.isEmpty() 
            ? UUID.randomUUID().toString() 
            : configuredInstanceId;
        LOG.infof("RedisLockProvider initialized with instance ID: %s", instanceId);
    }
    
    @Override
    public Optional<LockHandle> tryLock(String name, Duration lockAtMostFor) {
        String key = LOCK_PREFIX + name;
        
        // SET key value NX EX seconds
        // NX = Only set if not exists
        // EX = Set expiration in seconds
        String result = redis.setGet(
            key, 
            instanceId, 
            new SetArgs().nx().ex(lockAtMostFor)
        );
        
        // If result is null, lock was acquired (key didn't exist)
        // If result has a value, lock was NOT acquired (key already existed)
        if (result == null || result.equals(instanceId)) {
            LOG.debugf("Lock acquired: %s by instance %s", name, instanceId);
            return Optional.of(new RedisLockHandle(name, key));
        }
        
        LOG.debugf("Lock not acquired: %s (held by %s)", name, result);
        return Optional.empty();
    }
    
    /**
     * Redis-based lock handle implementation.
     */
    private class RedisLockHandle implements LockHandle {
        
        private final String name;
        private final String key;
        private volatile boolean released = false;
        
        RedisLockHandle(String name, String key) {
            this.name = name;
            this.key = key;
        }
        
        @Override
        public String name() {
            return name;
        }
        
        @Override
        public boolean extend(Duration duration) {
            if (released) {
                return false;
            }
            
            // Verify we still own the lock
            String currentOwner = redis.get(key);
            if (!instanceId.equals(currentOwner)) {
                LOG.warnf("Cannot extend lock %s: no longer owner", name);
                return false;
            }
            
            // Extend expiration
            redis.setex(key, duration.toSeconds(), instanceId);
            LOG.debugf("Lock extended: %s for %d seconds", name, duration.toSeconds());
            return true;
        }
        
        @Override
        public void close() {
            if (released) {
                return;
            }
            
            // Only release if we still own the lock
            String currentOwner = redis.get(key);
            if (instanceId.equals(currentOwner)) {
                redis.getdel(key);
                LOG.debugf("Lock released: %s", name);
            } else {
                LOG.warnf("Lock %s was not released: no longer owner (current: %s)", name, currentOwner);
            }
            
            released = true;
        }
    }
}
