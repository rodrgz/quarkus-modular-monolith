package org.acme.cache;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * OpenTelemetry metrics for cache operations.
 * <p>
 * Tracks hits, misses, evictions, and origin load duration
 * per cache name and cache level (L1/L2).
 * </p>
 */
@ApplicationScoped
public class CacheMetrics {

    private static final AttributeKey<String> CACHE_NAME = AttributeKey.stringKey("cache.name");
    private static final AttributeKey<String> CACHE_LEVEL = AttributeKey.stringKey("cache.level");

    private LongCounter hitCounter;
    private LongCounter missCounter;
    private LongCounter evictionCounter;
    private LongHistogram loadDuration;

    @Inject
    OpenTelemetry openTelemetry;

    @PostConstruct
    void init() {
        Meter meter = openTelemetry.getMeter("org.acme.cache");

        hitCounter = meter.counterBuilder("cache.hits")
                .setDescription("Number of cache hits")
                .setUnit("{hit}")
                .build();

        missCounter = meter.counterBuilder("cache.misses")
                .setDescription("Number of cache misses")
                .setUnit("{miss}")
                .build();

        evictionCounter = meter.counterBuilder("cache.evictions")
                .setDescription("Number of cache evictions")
                .setUnit("{eviction}")
                .build();

        loadDuration = meter.histogramBuilder("cache.load.duration")
                .setDescription("Time to load value from origin on cache miss")
                .setUnit("ms")
                .ofLongs()
                .build();
    }

    /**
     * Record a cache hit.
     *
     * @param cacheName The cache name
     * @param level     The cache level ("L1" or "L2")
     */
    public void recordHit(String cacheName, String level) {
        hitCounter.add(1, Attributes.of(CACHE_NAME, cacheName, CACHE_LEVEL, level));
    }

    /**
     * Record a cache miss (neither L1 nor L2 had the value).
     *
     * @param cacheName The cache name
     */
    public void recordMiss(String cacheName) {
        missCounter.add(1, Attributes.of(CACHE_NAME, cacheName));
    }

    /**
     * Record a cache eviction.
     *
     * @param cacheName The cache name
     * @param level     The cache level ("L1" or "L2")
     */
    public void recordEviction(String cacheName, String level) {
        evictionCounter.add(1, Attributes.of(CACHE_NAME, cacheName, CACHE_LEVEL, level));
    }

    /**
     * Record the time taken to load a value from the origin method.
     *
     * @param cacheName  The cache name
     * @param durationMs Duration in milliseconds
     */
    public void recordLoadDuration(String cacheName, long durationMs) {
        loadDuration.record(durationMs, Attributes.of(CACHE_NAME, cacheName));
    }
}
