package org.acme.scheduler;

import io.quarkus.redis.datasource.RedisDataSource;
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
 * <p>
 * Uses Redis SET with NX (only set if not exists) and EX (expiration)
 * to achieve atomic lock acquisition with automatic expiration.
 * </p>
 * 
 * <h2>Lock Key Format:</h2>
 * 
 * <pre>
 * lock:{name}
 * </pre>
 * 
 * <h2>Lock Value:</h2>
 * <p>
 * Contains the instance ID to ensure only the owner can release the lock.
 * </p>
 */
@ApplicationScoped
public class RedisLockProvider implements LockProvider {

    private static final Logger LOG = Logger.getLogger(RedisLockProvider.class);
    private static final String LOCK_PREFIX = "lock:";

    // Lua script for atomic lock release (compare-and-delete)
    private static final String RELEASE_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    // Lua script for atomic lock acquisition (setnx + expire)
    private static final String ACQUIRE_SCRIPT = "if redis.call('SETNX', KEYS[1], ARGV[1]) == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    // Lua script for atomic lock extension (compare-and-expire)
    private static final String EXTEND_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " +
            "else " +
            "  return 0 " +
            "end";

    private final RedisDataSource redisDataSource;
    private final String instanceId;

    @Inject
    public RedisLockProvider(
            RedisDataSource redisDataSource,
            @ConfigProperty(name = "scheduler.instance-id", defaultValue = "") String configuredInstanceId) {
        this.redisDataSource = redisDataSource;
        this.instanceId = configuredInstanceId.isEmpty()
                ? UUID.randomUUID().toString()
                : configuredInstanceId;
        LOG.infof("RedisLockProvider initialized with instance ID: %s", instanceId);
    }

    @Override
    public Optional<LockHandle> tryLock(String name, Duration lockAtMostFor) {
        String key = LOCK_PREFIX + name;

        try {
            // Use Lua script for atomic setnx + expire
            var result = redisDataSource.execute(
                    "EVAL", ACQUIRE_SCRIPT, "1", key, instanceId, String.valueOf(lockAtMostFor.toSeconds()));

            boolean acquired = "1".equals(String.valueOf(result));
            if (acquired) {
                LOG.debugf("Lock acquired: %s by instance %s", name, instanceId);
                return Optional.of(new RedisLockHandle(name, key));
            }

            LOG.debugf("Lock not acquired: %s (already held)", name);
            return Optional.empty();
        } catch (Exception e) {
            LOG.errorf("Error acquiring lock %s: %s", name, e.getMessage());
            return Optional.empty();
        }
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

            try {
                // Use Lua script for atomic compare-and-extend
                var result = redisDataSource.execute(
                        "EVAL", EXTEND_SCRIPT, "1", key, instanceId,
                        String.valueOf(duration.toMillis()));

                boolean extended = "1".equals(String.valueOf(result));
                if (extended) {
                    LOG.debugf("Lock extended: %s for %d ms", name, duration.toMillis());
                } else {
                    LOG.warnf("Cannot extend lock %s: no longer owner", name);
                }
                return extended;
            } catch (Exception e) {
                LOG.errorf("Error extending lock %s: %s", name, e.getMessage());
                return false;
            }
        }

        @Override
        public void close() {
            if (released) {
                return;
            }

            try {
                // Use Lua script for atomic compare-and-delete
                var result = redisDataSource.execute(
                        "EVAL", RELEASE_SCRIPT, "1", key, instanceId);

                boolean deleted = "1".equals(String.valueOf(result));
                if (deleted) {
                    LOG.debugf("Lock released: %s", name);
                } else {
                    LOG.warnf("Lock %s was not released: no longer owner", name);
                }
            } catch (Exception e) {
                LOG.errorf("Error releasing lock %s: %s", name, e.getMessage());
            }

            released = true;
        }
    }
}
