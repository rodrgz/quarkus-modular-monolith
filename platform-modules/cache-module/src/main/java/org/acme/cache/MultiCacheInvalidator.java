package org.acme.cache;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to automatically invalidate cache entries after method execution.
 */
@InterceptorBinding
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface MultiCacheInvalidator {

    /**
     * Name of the cache to invalidate.
     */
    @Nonbinding
    String cacheName();

    /**
     * If true, invalidates all entries in the cache.
     * If false, invalidates the entry corresponding to the method parameters (key
     * generation matches @MultiLevelCache).
     * Default: false
     */
    @Nonbinding
    boolean invalidateAll() default false;

    /**
     * The method name whose cache key should be used for invalidation.
     * If empty, uses the current method name (default behavior).
     * 
     * <p>
     * Example: If you cache with method "getData" but invalidate from "updateData",
     * set keySource = "getData" to match the cached key.
     * </p>
     * 
     * Default: "" (use current method name)
     */
    @Nonbinding
    String keySource() default "";
}
