package org.acme.cache;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Type;

/**
 * Interceptor for @MultiLevelCache annotation.
 * 
 * <h2>Behavior:</h2>
 * <ol>
 * <li>Generates cache key from method parameters</li>
 * <li>Checks L1 (Caffeine) → returns if hit</li>
 * <li>Checks L2 (Redis) → populates L1 and returns if hit</li>
 * <li>Calls origin method → caches result in L1 and L2</li>
 * </ol>
 * 
 * <h2>Fenced mode:</h2>
 * <p>
 * When {@code fenced=true}, acquires a fencing token before calling
 * origin and validates it before writing to cache, preventing stale writes.
 * </p>
 */
@MultiLevelCache(cacheName = "")
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class MultiLevelCacheInterceptor {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger
            .getLogger(MultiLevelCacheInterceptor.class);

    @Inject
    MultiLevelCacheManager cacheManager;

    @Inject
    FencingTokenProvider fencingTokenProvider;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        MultiLevelCache annotation = context.getMethod().getAnnotation(MultiLevelCache.class);

        if (annotation == null) {
            return context.proceed();
        }

        String cacheName = annotation.cacheName();
        String key = generateKey(context);
        Type returnType = context.getMethod().getGenericReturnType();

        // Register cache config (idempotent)
        cacheManager.registerCache(cacheName, MultiLevelCacheManager.CacheConfig.from(annotation));

        if (annotation.fenced()) {
            return interceptFenced(cacheName, key, returnType, context);
        }

        // Non-fenced: use getOrLoad for thundering herd protection
        return cacheManager.getOrLoad(cacheName, key, returnType, () -> {
            try {
                return context.proceed();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Origin method failed", e);
            }
        });
    }

    /**
     * Fenced cache flow: acquire token → call origin → validate token → write.
     * Uses get() instead of getOrLoad() because fencing requires explicit
     * control between origin call and cache write.
     */
    private Object interceptFenced(String cacheName, String key, Type returnType,
            InvocationContext context) throws Exception {
        // Try cache first
        String raw = cacheManager.getRaw(cacheName, key);
        if (raw != null) {
            LOG.debugf("Fenced cache HIT: %s:%s", cacheName, key);
            return cacheManager.deserialize(raw, returnType);
        }

        LOG.debugf("Fenced cache MISS: %s:%s", cacheName, key);

        // Acquire fencing token BEFORE calling origin
        long token = fencingTokenProvider.nextToken(cacheName, key);

        // Call origin
        Object result = context.proceed();

        // Write with fencing — rejected if a newer token was issued while we computed
        boolean shouldCache = result != null || cacheManager.getCacheConfig(cacheName).cacheNulls();
        if (shouldCache) {
            LOG.debugf("Fenced cache PUT: %s:%s (null=%b)", cacheName, key, result == null);
            cacheManager.putFenced(cacheName, key, result, token);
        }

        return result;
    }

    /**
     * Generate cache key from method name and parameters.
     */
    private String generateKey(InvocationContext context) {
        return CacheKeyGenerator.generate(
                context.getMethod().getName(),
                context.getParameters());
    }
}
