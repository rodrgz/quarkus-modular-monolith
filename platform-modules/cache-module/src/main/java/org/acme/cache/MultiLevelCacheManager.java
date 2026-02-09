package org.acme.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.pubsub.PubSubCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Multi-level cache manager with Caffeine (L1) and Redis (L2).
 * 
 * <h2>Features:</h2>
 * <ul>
 * <li>L1 (Caffeine): In-memory cache per pod</li>
 * <li>L2 (Redis): Distributed cache across pods</li>
 * <li>Distributed invalidation via Redis Pub/Sub</li>
 * <li>Automatic L1 population on L2 hit</li>
 * <li>Fallback to L1 when Redis unavailable</li>
 * </ul>
 */
@ApplicationScoped
@Startup
public class MultiLevelCacheManager {

    private static final Logger LOG = Logger.getLogger(MultiLevelCacheManager.class);
    private static final String CACHE_PREFIX = "cache:";
    static final String NULL_SENTINEL = "__NULL_SENTINEL__";

    private final Map<String, Cache<String, CacheEntry>> l1Caches = new ConcurrentHashMap<>();
    private final Map<String, CacheConfig> cacheConfigs = new ConcurrentHashMap<>();

    private final ValueCommands<String, String> redisValue;
    private final PubSubCommands<String> redisPubSub;
    private final ObjectMapper objectMapper;

    private final ExecutorService invalidationExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private final String instanceId;

    @ConfigProperty(name = "cache.multilevel.invalidation.channel", defaultValue = "cache-invalidation")
    String invalidationChannel;

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    @Inject
    CacheMetrics cacheMetrics;

    @Inject
    FencingTokenProvider fencingTokenProvider;

    // Track if L1 loader was executed to prevent double counting hits
    private final ThreadLocal<Boolean> l1LoaderExecuted = ThreadLocal.withInitial(() -> false);

    @Inject
    public MultiLevelCacheManager(RedisDataSource redisDataSource, ObjectMapper objectMapper) {
        this.redisValue = redisDataSource.value(String.class);
        this.redisPubSub = redisDataSource.pubsub(String.class);
        this.objectMapper = objectMapper;
        this.instanceId = java.util.UUID.randomUUID().toString();
        LOG.infof("MultiLevelCacheManager initialized with instance ID: %s", instanceId);
    }

    @PostConstruct
    void init() {
        // Subscribe to invalidation messages
        invalidationExecutor.submit(() -> {
            LOG.info("Starting cache invalidation listener on channel: " + invalidationChannel);
            Consumer<String> consumer = this::handleInvalidationMessage;
            redisPubSub.subscribe(invalidationChannel, consumer);
        });
    }

    @PreDestroy
    void shutdown() {
        running = false;
        invalidationExecutor.shutdownNow();
    }

    public CacheConfig getCacheConfig(String cacheName) {
        return cacheConfigs.get(cacheName);
    }

    /**
     * Register a cache with configuration.
     * Uses putIfAbsent to prevent overwriting existing caches.
     */
    public void registerCache(String cacheName, CacheConfig config) {
        // Check for config conflicts
        CacheConfig existing = cacheConfigs.putIfAbsent(cacheName, config);
        if (existing != null && !existing.equals(config)) {
            LOG.warnf("Cache '%s' registered with conflicting config. Using first registration. " +
                    "First: %s, Attempted: %s", cacheName, existing, config);
        }

        if (config.l1Enabled()) {
            l1Caches.computeIfAbsent(cacheName, k -> {
                Cache<String, CacheEntry> cache = Caffeine.newBuilder()
                        .maximumSize(config.l1MaxSize())
                        .expireAfterWrite(Duration.ofSeconds(config.l1TtlSeconds()))
                        .build();
                LOG.debugf("Registered L1 cache: %s (maxSize=%d, ttl=%ds)",
                        cacheName, config.l1MaxSize(), config.l1TtlSeconds());
                return cache;
            });
        }
    }

    /**
     * Get raw serialized value from cache (L1 first, then L2).
     * 
     * @return Serialized value, NULL_SENTINEL, or null if miss.
     */
    public String getRaw(String cacheName, String key) {
        CacheConfig config = getOrCreateConfig(cacheName);

        // Try L1
        if (config.l1Enabled()) {
            CacheEntry entry = getL1Cache(cacheName, config).getIfPresent(key);
            if (entry != null) {
                return entry.value();
            }
        }

        // Try L2
        if (config.l2Enabled()) {
            try {
                String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                String value = redisValue.get(redisKey);

                if (value != null) {
                    // Populate L1
                    if (config.l1Enabled()) {
                        getL1Cache(cacheName, config).put(key, new CacheEntry(value, false));
                    }
                    return value;
                }
            } catch (Exception e) {
                LOG.warnf("L2 cache error for %s:%s - %s", cacheName, key, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get raw value specifically from L1.
     * Internal tool for verification in tests.
     */
    String getRawL1(String cacheName, String key) {
        CacheConfig config = getOrCreateConfig(cacheName);
        CacheEntry entry = getL1Cache(cacheName, config).getIfPresent(key);
        return entry != null ? entry.value() : null;
    }

    /**
     * Get raw value specifically from L2.
     * Internal tool for verification in tests.
     */
    String getRawL2(String cacheName, String key) {
        try {
            String redisKey = CACHE_PREFIX + cacheName + ":" + key;
            return redisValue.get(redisKey);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get value from cache (L1 first, then L2).
     */
    public <T> T get(String cacheName, String key, Type returnType) {
        CacheConfig config = getOrCreateConfig(cacheName);

        // Try L1
        if (config.l1Enabled()) {
            CacheEntry entry = getL1Cache(cacheName, config).getIfPresent(key);
            if (entry != null) {
                T value = deserialize(entry.value(), returnType);
                if (!checkExpireCondition(cacheName, key, value, config.expireWhenClass())) {
                    LOG.debugf("L1 cache HIT: %s:%s", cacheName, key);
                    return value;
                }
                LOG.debugf("L1 cache EXPIRED by condition: %s:%s", cacheName, key);
                // Evict stale entry from L1
                getL1Cache(cacheName, config).invalidate(key);
            }
        }

        // Try L2
        if (config.l2Enabled()) {
            try {
                String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                String value = redisValue.get(redisKey);

                if (value != null) {
                    T deserializedValue = deserialize(value, returnType);

                    if (!checkExpireCondition(cacheName, key, deserializedValue, config.expireWhenClass())) {
                        LOG.debugf("L2 cache HIT: %s:%s", cacheName, key);

                        // Populate L1
                        if (config.l1Enabled()) {
                            getL1Cache(cacheName, config).put(key, new CacheEntry(value, false));
                        }

                        return deserializedValue;
                    }
                    LOG.debugf("L2 cache EXPIRED by condition: %s:%s", cacheName, key);
                    // Evict stale entry from L2
                    redisValue.getdel(redisKey);
                }
            } catch (Exception e) {
                LOG.warnf("L2 cache error for %s:%s - %s", cacheName, key, e.getMessage());
                if (!config.l1AsFallback()) {
                    throw e;
                }
            }
        }

        LOG.debugf("Cache MISS: %s:%s", cacheName, key);
        return null;
    }

    /**
     * Get value from cache with thundering herd protection.
     * <p>
     * Uses Caffeine's {@code get(key, loader)} to coalesce concurrent requests
     * for the same key — only one thread invokes the loader, others wait for the
     * result.
     * </p>
     * <p>
     * Loader order: L2 (Redis) → origin supplier → store in L2
     * </p>
     *
     * @param cacheName  The cache name
     * @param key        The cache key
     * @param returnType The return type for deserialization
     * @param origin     Supplier that calls the origin method (e.g. database)
     * @return The cached or loaded value (may be null if origin returns null)
     */
    public <T> T getOrLoad(String cacheName, String key, Type returnType, Supplier<T> origin) {
        CacheConfig config = getOrCreateConfig(cacheName);

        // Caffeine.get(key, loader) provides thundering herd protection:
        // Only one thread executes the loader for a given key; others block and wait.
        // Even if config.l1Enabled() is false, we use a Caffeine instance here
        // as a "coalescing buffer" to prevent thundering herd on L2/Origin.
        Cache<String, CacheEntry> l1 = getL1Cache(cacheName, config);

        boolean previousLoaderState = l1LoaderExecuted.get();
        l1LoaderExecuted.set(false);
        try {
            CacheEntry entry = l1.get(key, k -> {
                l1LoaderExecuted.set(true);
                // Inside the loader: try L2 first, then origin
                T loaded = loadFromL2OrOrigin(cacheName, k, returnType, origin, config);
                if (loaded != null) {
                    return new CacheEntry(serialize(loaded), false);
                }
                // Origin returned null — cache sentinel if cacheNulls enabled
                if (config.cacheNulls()) {
                    LOG.debugf("Caching null sentinel: %s:%s", cacheName, k);
                    return new CacheEntry(NULL_SENTINEL, true);
                }
                return null;
            });

            if (entry == null) {
                cacheMetrics.recordMiss(cacheName);
                LOG.debugf("Cache MISS (origin null): %s:%s", cacheName, key);
                return null;
            }

            // Check for null sentinel
            if (entry.nullSentinel()) {
                if (!l1LoaderExecuted.get()) {
                    cacheMetrics.recordHit(cacheName, "L1");
                    LOG.debugf("L1 cache HIT (null sentinel): %s:%s", cacheName, key);
                }
                return null;
            }

            T value = deserialize(entry.value(), returnType);

            // Check expire condition
            if (checkExpireCondition(cacheName, key, value, config.expireWhenClass())) {
                LOG.debugf("Cache EXPIRED by condition: %s:%s", cacheName, key);
                l1.invalidate(key);
                cacheMetrics.recordEviction(cacheName, "L1");
                // Also evict from L2 to prevent stale hits on next load
                if (config.l2Enabled()) {
                    try {
                        String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                        redisValue.getdel(redisKey);
                        cacheMetrics.recordEviction(cacheName, "L2");
                    } catch (Exception e) {
                        LOG.warnf("Failed to evict stale L2 entry %s:%s - %s", cacheName, key, e.getMessage());
                    }
                }
                return null;
            }

            if (!l1LoaderExecuted.get()) {
                cacheMetrics.recordHit(cacheName, "L1");
            }
            return value;
        } finally {
            l1LoaderExecuted.set(previousLoaderState);
        }
    }

    /**
     * Load from L2 (Redis) or fall back to origin supplier.
     * If loaded from origin, stores result in L2.
     */
    private <T> T loadFromL2OrOrigin(String cacheName, String key, Type returnType,
            Supplier<T> origin, CacheConfig config) {
        // Try L2
        if (config.l2Enabled()) {
            try {
                String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                String value = redisValue.get(redisKey);
                if (value != null) {
                    // Check for null sentinel in L2
                    if (NULL_SENTINEL.equals(value)) {
                        cacheMetrics.recordHit(cacheName, "L2");
                        LOG.debugf("L2 cache HIT (null sentinel): %s:%s", cacheName, key);
                        return null;
                    }
                    T deserialized = deserialize(value, returnType);
                    if (!checkExpireCondition(cacheName, key, deserialized, config.expireWhenClass())) {
                        cacheMetrics.recordHit(cacheName, "L2");
                        LOG.debugf("L2 cache HIT: %s:%s", cacheName, key);
                        return deserialized;
                    }
                    LOG.debugf("L2 cache EXPIRED by condition: %s:%s", cacheName, key);
                    cacheMetrics.recordEviction(cacheName, "L2");
                    redisValue.getdel(redisKey);
                }
            } catch (Exception e) {
                LOG.warnf("L2 cache error for %s:%s - %s", cacheName, key, e.getMessage());
                if (!config.l1AsFallback()) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Cache miss — call origin with timing
        LOG.debugf("Cache MISS, loading from origin: %s:%s", cacheName, key);
        cacheMetrics.recordMiss(cacheName);
        long startNs = System.nanoTime();
        T result = origin.get();
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        cacheMetrics.recordLoadDuration(cacheName, durationMs);

        // Store in L2
        if (config.l2Enabled()) {
            try {
                String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                if (result != null) {
                    String serialized = serialize(result);
                    redisValue.set(redisKey, serialized,
                            new SetArgs().ex(Duration.ofSeconds(config.l2TtlSeconds())));
                    LOG.debugf("Stored in L2: %s:%s", cacheName, key);
                } else if (config.cacheNulls()) {
                    // Store null sentinel in L2 with shorter TTL
                    redisValue.set(redisKey, NULL_SENTINEL,
                            new SetArgs().ex(Duration.ofSeconds(config.nullTtlSeconds())));
                    LOG.debugf("Stored null sentinel in L2: %s:%s (ttl=%ds)",
                            cacheName, key, config.nullTtlSeconds());
                }
            } catch (Exception e) {
                LOG.warnf("Failed to store in L2 %s:%s - %s", cacheName, key, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Put value in both cache levels.
     */
    public <T> void put(String cacheName, String key, T value) {
        CacheConfig config = getOrCreateConfig(cacheName);
        String serialized = serialize(value);

        // Put in L1
        if (config.l1Enabled()) {
            getL1Cache(cacheName, config).put(key, new CacheEntry(serialized, false));
        }

        // Put in L2
        if (config.l2Enabled()) {
            try {
                String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                redisValue.set(redisKey, serialized, new SetArgs().ex(Duration.ofSeconds(config.l2TtlSeconds())));
                LOG.debugf("Cached %s:%s (L1=%b, L2=%b)", cacheName, key, config.l1Enabled(), config.l2Enabled());
            } catch (Exception e) {
                LOG.warnf("Failed to put in L2 cache %s:%s - %s", cacheName, key, e.getMessage());
            }
        }
    }

    /**
     * Put value with fencing token validation.
     * <p>
     * Only writes if the provided token is >= the current stored token.
     * This prevents stale writes from delayed processes that lost their lock.
     * </p>
     *
     * @param cacheName    The cache name
     * @param key          The cache key
     * @param value        The value to cache
     * @param fencingToken The fencing token acquired before computation
     * @return true if write was accepted, false if rejected (stale token)
     */
    public <T> boolean putFenced(String cacheName, String key, T value, long fencingToken) {
        if (!fencingTokenProvider.validateAndSetToken(cacheName, key, fencingToken)) {
            LOG.warnf("Fenced write rejected for %s:%s (token=%d)", cacheName, key, fencingToken);
            return false;
        }
        put(cacheName, key, value);
        return true;
    }

    /**
     * Evict from local L1 cache.
     */
    public void evictFromL1(String cacheName, String key) {
        Cache<String, CacheEntry> l1Cache = l1Caches.get(cacheName);
        if (l1Cache != null) {
            l1Cache.invalidate(key);
            LOG.debugf("Evicted from L1: %s:%s", cacheName, key);
        }
    }

    /**
     * Evict from L2 (Redis) cache.
     */
    public void evictFromL2(String cacheName, String key) {
        try {
            String redisKey = CACHE_PREFIX + cacheName + ":" + key;
            redisValue.getdel(redisKey);
            LOG.debugf("Evicted from L2: %s:%s", cacheName, key);
        } catch (Exception e) {
            LOG.warnf("Failed to evict from L2 %s:%s - %s", cacheName, key, e.getMessage());
        }
    }

    /**
     * Clear all entries from L1 cache.
     */
    public void clearL1(String cacheName) {
        Cache<String, CacheEntry> l1Cache = l1Caches.get(cacheName);
        if (l1Cache != null) {
            l1Cache.invalidateAll();
            LOG.debugf("Cleared L1 cache: %s", cacheName);
        }
    }

    /**
     * Clear all entries from L2 (Redis) cache using SCAN pattern.
     */
    public void clearL2(String cacheName) {
        try {
            io.quarkus.redis.datasource.keys.KeyCommands<String> keyCommands = redisValue.getDataSource().key();
            String pattern = CACHE_PREFIX + cacheName + ":*";

            // Use SCAN to find all keys matching pattern
            java.util.List<String> keys = keyCommands.keys(pattern);
            if (!keys.isEmpty()) {
                keyCommands.del(keys.toArray(new String[0]));
                LOG.debugf("Cleared L2 cache: %s (%d keys deleted)", cacheName, keys.size());
            } else {
                LOG.debugf("Cleared L2 cache: %s (no keys found)", cacheName);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to clear L2 cache %s - %s", cacheName, e.getMessage());
        }
    }

    /**
     * Publish invalidation message to all pods using JSON format.
     */
    public void publishInvalidation(String cacheName, String key) {
        try {
            InvalidationMessage msg = new InvalidationMessage(cacheName, key, instanceId);
            String json = objectMapper.writeValueAsString(msg);
            redisPubSub.publish(invalidationChannel, json);
            LOG.debugf("Published invalidation: %s:%s", cacheName, key);
        } catch (Exception e) {
            LOG.warnf("Failed to publish invalidation %s:%s - %s", cacheName, key, e.getMessage());
        }
    }

    // ===== Private Methods =====

    private boolean checkExpireCondition(String cacheName, String key, Object value,
            Class<? extends ExpireCondition> conditionClass) {
        if (conditionClass == null || conditionClass.equals(NeverExpire.class)) {
            return false;
        }
        try {
            ExpireCondition condition = io.quarkus.arc.Arc.container().instance(conditionClass).get();
            if (condition == null) {
                LOG.warnf("ExpireCondition bean not found for class: %s", conditionClass.getName());
                return false;
            }
            return condition.isExpired(cacheName, key, value);
        } catch (Exception e) {
            LOG.errorf("Error checking expire condition for %s:%s - %s", cacheName, key, e.getMessage());
            return false;
        }
    }

    private void handleInvalidationMessage(String message) {
        try {
            InvalidationMessage msg = objectMapper.readValue(message, InvalidationMessage.class);

            // Skip self-published messages
            if (instanceId.equals(msg.sourceInstanceId())) {
                LOG.debugf("Skipping self-published invalidation: %s:%s", msg.cacheName(), msg.key());
                return;
            }

            if ("*".equals(msg.key())) {
                clearL1(msg.cacheName());
            } else {
                evictFromL1(msg.cacheName(), msg.key());
            }
        } catch (Exception e) {
            LOG.errorf("Error handling invalidation message: %s - %s", message, e.getMessage());
        }
    }

    Cache<String, CacheEntry> getL1Cache(String cacheName, CacheConfig config) {
        return l1Caches.computeIfAbsent(cacheName, k -> {
            long ttl = config.l1Enabled() ? config.l1TtlSeconds() : 1; // 1s for coalescing if disabled
            int size = config.l1Enabled() ? config.l1MaxSize() : 100;
            return Caffeine.newBuilder()
                    .maximumSize(size)
                    .expireAfterWrite(Duration.ofSeconds(ttl))
                    .build();
        });
    }

    private CacheConfig getOrCreateConfig(String cacheName) {
        return cacheConfigs.computeIfAbsent(cacheName, k -> CacheConfig.defaults());
    }

    private String serialize(Object value) {
        if (value == null) {
            return NULL_SENTINEL;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cache value", e);
        }
    }

    public <T> T deserialize(String value, Type returnType) {
        if (value == null || NULL_SENTINEL.equals(value)) {
            return null;
        }
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(returnType);
            return objectMapper.readValue(value, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize cache value", e);
        }
    }

    /**
     * Internal cache entry wrapper.
     * The nullSentinel flag indicates if this entry represents a cached null.
     */
    record CacheEntry(String value, boolean nullSentinel) {
    }

    /**
     * Invalidation message for pub/sub.
     */
    private record InvalidationMessage(String cacheName, String key, String sourceInstanceId) {
    }

    /**
     * Cache configuration record.
     */
    public record CacheConfig(
            boolean l1Enabled,
            long l1TtlSeconds,
            int l1MaxSize,
            boolean l1AsFallback,
            boolean l2Enabled,
            long l2TtlSeconds,
            Class<? extends ExpireCondition> expireWhenClass,
            boolean cacheNulls,
            long nullTtlSeconds) {
        public static CacheConfig defaults() {
            return new CacheConfig(true, 60, 1000, true, true, 300, NeverExpire.class, false, 30);
        }

        public static CacheConfig from(MultiLevelCache annotation) {
            long l1Seconds = annotation.l1TtlUnit().toSeconds(annotation.l1Ttl());
            long l2Seconds = annotation.l2TtlUnit().toSeconds(annotation.l2Ttl());
            long nullSeconds = annotation.nullTtlUnit().toSeconds(annotation.nullTtl());

            return new CacheConfig(
                    annotation.l1Enabled(),
                    l1Seconds,
                    annotation.l1MaxSize(),
                    annotation.l1AsFallback(),
                    annotation.l2Enabled(),
                    l2Seconds,
                    annotation.expireWhen(),
                    annotation.cacheNulls(),
                    nullSeconds);
        }
    }
}
