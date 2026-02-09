package org.acme.cache;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

/**
 * Interceptor for @MultiCacheInvalidator annotation.
 * Automatically invalidates cache entries after method execution.
 */
@MultiCacheInvalidator(cacheName = "")
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 1) // Run after other application interceptors
public class MultiCacheInvalidatorInterceptor {

    private static final Logger LOG = Logger.getLogger(MultiCacheInvalidatorInterceptor.class);

    @Inject
    CacheInvalidator cacheInvalidator;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        // Proceed with the method execution first
        Object result = context.proceed();

        try {
            MultiCacheInvalidator annotation = context.getMethod().getAnnotation(MultiCacheInvalidator.class);
            if (annotation != null) {
                invalidate(annotation, context);
            } else {
                // Try class level
                annotation = context.getMethod().getDeclaringClass().getAnnotation(MultiCacheInvalidator.class);
                if (annotation != null) {
                    invalidate(annotation, context);
                }
            }
        } catch (Exception e) {
            LOG.errorf("Error during cache invalidation: %s", e.getMessage());
        }

        return result;
    }

    private void invalidate(MultiCacheInvalidator annotation, InvocationContext context) {
        String cacheName = annotation.cacheName();
        if (annotation.invalidateAll()) {
            cacheInvalidator.invalidateAll(cacheName);
        } else {
            String key = generateKey(context);
            cacheInvalidator.invalidate(cacheName, key);
        }
    }

    /**
     * Generate cache key from method name and parameters.
     * Uses keySource if specified, otherwise current method name.
     */
    private String generateKey(InvocationContext context) {
        MultiCacheInvalidator annotation = context.getMethod().getAnnotation(MultiCacheInvalidator.class);

        // Use keySource if specified, otherwise current method name
        String methodName = (annotation != null && !annotation.keySource().isEmpty())
                ? annotation.keySource()
                : context.getMethod().getName();

        return CacheKeyGenerator.generate(methodName, context.getParameters());
    }
}
