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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Multi-level cache manager with Caffeine (L1) and Redis (L2).
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li>L1 (Caffeine): In-memory cache per pod</li>
 *   <li>L2 (Redis): Distributed cache across pods</li>
 *   <li>Distributed invalidation via Redis Pub/Sub</li>
 *   <li>Automatic L1 population on L2 hit</li>
 *   <li>Fallback to L1 when Redis unavailable</li>
 * </ul>
 */
@ApplicationScoped
@Startup
public class MultiLevelCacheManager {
    
    private static final Logger LOG = Logger.getLogger(MultiLevelCacheManager.class);
    private static final String CACHE_PREFIX = "cache:";
    
    private final Map<String, Cache<String, CacheEntry>> l1Caches = new ConcurrentHashMap<>();
    private final Map<String, CacheConfig> cacheConfigs = new ConcurrentHashMap<>();
    
    private final ValueCommands<String, String> redisValue;
    private final PubSubCommands<String> redisPubSub;
    private final ObjectMapper objectMapper;
    
    private final ExecutorService invalidationExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    
    @ConfigProperty(name = "cache.multilevel.invalidation.channel", defaultValue = "cache-invalidation")
    String invalidationChannel;
    
    @Inject
    public MultiLevelCacheManager(RedisDataSource redisDataSource, ObjectMapper objectMapper) {
        this.redisValue = redisDataSource.value(String.class);
        this.redisPubSub = redisDataSource.pubsub(String.class);
        this.objectMapper = objectMapper;
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
    
    /**
     * Register a cache with configuration.
     */
    public void registerCache(String cacheName, CacheConfig config) {
        cacheConfigs.put(cacheName, config);
        
        if (config.l1Enabled()) {
            Cache<String, CacheEntry> l1Cache = Caffeine.newBuilder()
                .maximumSize(config.l1MaxSize())
                .expireAfterWrite(Duration.ofSeconds(config.l1TtlSeconds()))
                .build();
            l1Caches.put(cacheName, l1Cache);
            LOG.debugf("Registered L1 cache: %s (maxSize=%d, ttl=%ds)", 
                cacheName, config.l1MaxSize(), config.l1TtlSeconds());
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
                LOG.debugf("L1 cache HIT: %s:%s", cacheName, key);
                return deserialize(entry.value(), returnType);
            }
        }
        
        // Try L2
        if (config.l2Enabled()) {
            try {
                String redisKey = CACHE_PREFIX + cacheName + ":" + key;
                String value = redisValue.get(redisKey);
                
                if (value != null) {
                    LOG.debugf("L2 cache HIT: %s:%s", cacheName, key);
                    
                    // Populate L1
                    if (config.l1Enabled()) {
                        getL1Cache(cacheName, config).put(key, new CacheEntry(value));
                    }
                    
                    return deserialize(value, returnType);
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
     * Put value in both cache levels.
     */
    public <T> void put(String cacheName, String key, T value) {
        CacheConfig config = getOrCreateConfig(cacheName);
        String serialized = serialize(value);
        
        // Put in L1
        if (config.l1Enabled()) {
            getL1Cache(cacheName, config).put(key, new CacheEntry(serialized));
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
     * Publish invalidation message to all pods.
     */
    public void publishInvalidation(String cacheName, String key) {
        try {
            String message = cacheName + "|" + key;
            redisPubSub.publish(invalidationChannel, message);
            LOG.debugf("Published invalidation: %s:%s", cacheName, key);
        } catch (Exception e) {
            LOG.warnf("Failed to publish invalidation %s:%s - %s", cacheName, key, e.getMessage());
        }
    }
    
    // ===== Private Methods =====
    
    private void handleInvalidationMessage(String message) {
        try {
            String[] parts = message.split("\\|", 2);
            if (parts.length != 2) {
                LOG.warnf("Invalid invalidation message: %s", message);
                return;
            }
            
            String cacheName = parts[0];
            String key = parts[1];
            
            if ("*".equals(key)) {
                clearL1(cacheName);
            } else {
                evictFromL1(cacheName, key);
            }
        } catch (Exception e) {
            LOG.errorf("Error handling invalidation message: %s - %s", message, e.getMessage());
        }
    }
    
    private Cache<String, CacheEntry> getL1Cache(String cacheName, CacheConfig config) {
        return l1Caches.computeIfAbsent(cacheName, k -> 
            Caffeine.newBuilder()
                .maximumSize(config.l1MaxSize())
                .expireAfterWrite(Duration.ofSeconds(config.l1TtlSeconds()))
                .build()
        );
    }
    
    private CacheConfig getOrCreateConfig(String cacheName) {
        return cacheConfigs.computeIfAbsent(cacheName, k -> CacheConfig.defaults());
    }
    
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cache value", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T deserialize(String value, Type returnType) {
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(returnType);
            return objectMapper.readValue(value, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize cache value", e);
        }
    }
    
    /**
     * Internal cache entry wrapper.
     */
    private record CacheEntry(String value) {}
    
    /**
     * Cache configuration record.
     */
    public record CacheConfig(
        boolean l1Enabled,
        long l1TtlSeconds,
        int l1MaxSize,
        boolean l1AsFallback,
        boolean l2Enabled,
        long l2TtlSeconds
    ) {
        public static CacheConfig defaults() {
            return new CacheConfig(true, 60, 1000, true, true, 300);
        }
        
        public static CacheConfig from(MultiLevelCache annotation) {
            return new CacheConfig(
                annotation.l1Enabled(),
                annotation.l1TtlSeconds(),
                annotation.l1MaxSize(),
                annotation.l1AsFallback(),
                annotation.l2Enabled(),
                annotation.l2TtlSeconds()
            );
        }
    }
}
