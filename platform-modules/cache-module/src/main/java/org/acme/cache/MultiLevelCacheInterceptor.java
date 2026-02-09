package org.acme.cache;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Interceptor for @MultiLevelCache annotation.
 * 
 * <h2>Behavior:</h2>
 * <ol>
 *   <li>Generates cache key from method parameters</li>
 *   <li>Checks L1 (Caffeine) → returns if hit</li>
 *   <li>Checks L2 (Redis) → populates L1 and returns if hit</li>
 *   <li>Calls origin method → caches result in L1 and L2</li>
 * </ol>
 */
@MultiLevelCache(cacheName = "")
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class MultiLevelCacheInterceptor {
    
    private static final Logger LOG = Logger.getLogger(MultiLevelCacheInterceptor.class);
    
    @Inject
    MultiLevelCacheManager cacheManager;
    
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
        
        // Try to get from cache
        Object cached = cacheManager.get(cacheName, key, returnType);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - call origin
        Object result = context.proceed();
        
        // Cache the result (null values are not cached)
        if (result != null) {
            cacheManager.put(cacheName, key, result);
        }
        
        return result;
    }
    
    /**
     * Generate cache key from method name and parameters.
     */
    private String generateKey(InvocationContext context) {
        Method method = context.getMethod();
        Object[] params = context.getParameters();
        
        if (params == null || params.length == 0) {
            return method.getName();
        }
        
        String paramKey = Arrays.stream(params)
            .map(p -> p == null ? "null" : p.toString())
            .collect(Collectors.joining(":"));
        
        return method.getName() + ":" + paramKey;
    }
}
