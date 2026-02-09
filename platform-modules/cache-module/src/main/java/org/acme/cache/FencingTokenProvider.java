package org.acme.cache;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Provides monotonically increasing fencing tokens via Redis INCR.
 * <p>
 * Fencing tokens prevent stale writes from delayed processes that lost
 * their distributed lock (e.g. due to GC pause or network partition).
 * A process must obtain a token before writing to the cache, and the
 * write is only accepted if its token is >= the current stored token.
 * </p>
 *
 * <h2>How it works:</h2>
 * <ol>
 * <li>Process acquires distributed lock</li>
 * <li>Process calls {@link #nextToken} to get a fencing token</li>
 * <li>Process computes the value</li>
 * <li>Process calls {@link #validateAndSetToken} before writing — only writes
 * if token is current</li>
 * </ol>
 */
@ApplicationScoped
public class FencingTokenProvider {

    private static final Logger LOG = Logger.getLogger(FencingTokenProvider.class);
    private static final String FENCE_PREFIX = "fence:";

    private static final String VALIDATE_SCRIPT = "local currentToken = redis.call('GET', KEYS[1]) " +
            "if (not currentToken) or (tonumber(ARGV[1]) >= tonumber(currentToken)) then " +
            "    redis.call('SET', KEYS[1], ARGV[1]) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    private final RedisDataSource redisDataSource;
    private final ValueCommands<String, Long> redisLong;

    @Inject
    public FencingTokenProvider(RedisDataSource redisDataSource) {
        this.redisDataSource = redisDataSource;
        this.redisLong = redisDataSource.value(Long.class);
    }

    /**
     * Acquire a new fencing token for the given cache key.
     * Each call atomically increments the counter, guaranteeing uniqueness.
     *
     * @param cacheName The cache name
     * @param key       The cache key
     * @return A monotonically increasing token
     */
    public long nextToken(String cacheName, String key) {
        String fenceKey = FENCE_PREFIX + cacheName + ":" + key;
        long token = redisLong.incr(fenceKey);
        LOG.debugf("Acquired fencing token %d for %s:%s", token, cacheName, key);
        return token;
    }

    /**
     * Validate a fencing token and atomically set it if valid.
     * A token is valid if it is >= the current stored token.
     * <p>
     * Uses a Lua script for atomic compare-and-set to prevent races
     * between concurrent validate calls.
     * </p>
     *
     * @param cacheName The cache name
     * @param key       The cache key
     * @param token     The token to validate
     * @return true if the token is valid (>= current), false if stale
     */
    public boolean validateAndSetToken(String cacheName, String key, long token) {
        String fenceKey = FENCE_PREFIX + cacheName + ":" + key;
        try {
            Object result = redisDataSource.execute("EVAL", VALIDATE_SCRIPT, "1", fenceKey, String.valueOf(token));
            boolean valid = "1".equals(String.valueOf(result));
            if (valid) {
                LOG.debugf("Fencing token %d validated for %s:%s", token, cacheName, key);
                return true;
            }
            LOG.warnf("Stale fencing token %d — write rejected for %s:%s", token, cacheName, key);
            return false;
        } catch (Exception e) {
            LOG.errorf("Error validating fencing token for %s:%s - %s", cacheName, key, e.getMessage());
            // Fail-open: allow the write if token validation fails
            return true;
        }
    }
}
