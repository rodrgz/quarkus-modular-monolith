package org.acme.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Utility for generating stable cache keys from method name and parameters.
 * 
 * <h2>Key Generation Strategy:</h2>
 * <ol>
 * <li>Serialize parameters to JSON</li>
 * <li>Generate SHA-256 hash of JSON</li>
 * <li>Fallback to toString() if serialization fails</li>
 * </ol>
 * 
 * <p>
 * This ensures stable keys across JVM restarts and different pods,
 * unlike relying on Object.toString() which uses identity hash.
 * </p>
 */
public final class CacheKeyGenerator {

    private static final Logger LOG = Logger.getLogger(CacheKeyGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CacheKeyGenerator() {
        // Utility class
    }

    /**
     * Generate cache key from method name and parameters.
     * 
     * @param methodName The method name (or explicit key source)
     * @param params     The method parameters
     * @return Stable cache key
     */
    public static String generate(String methodName, Object[] params) {
        if (params == null || params.length == 0) {
            return methodName;
        }

        // Try JSON-based hash for stability
        try {
            String serialized = MAPPER.writeValueAsString(params);
            String hash = sha256Hex(serialized);
            return methodName + ":" + hash;
        } catch (JsonProcessingException e) {
            LOG.warnf("Failed to serialize params for cache key, falling back to toString(): %s", e.getMessage());
            return generateFallback(methodName, params);
        }
    }

    /**
     * Fallback key generation using toString().
     * Logs warning for objects without explicit toString().
     */
    private static String generateFallback(String methodName, Object[] params) {
        String paramKey = Arrays.stream(params)
                .map(p -> {
                    if (p == null)
                        return "null";
                    String str = p.toString();
                    // Detect default Object.toString() pattern (ClassName@hexHash)
                    if (str.matches(".*@[0-9a-f]+$")) {
                        LOG.warnf("Parameter %s uses default toString() - cache key may be unstable",
                                p.getClass().getName());
                    }
                    return str;
                })
                .collect(Collectors.joining(":"));
        return methodName + ":" + paramKey;
    }

    /**
     * Generate SHA-256 hex hash of input string.
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
