package org.acme.cache;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

@QuarkusTest
public class CacheKeyGeneratorTest {

    @Test
    public void test_K01_methodWithoutParams_returnsNameOnly() {
        String key = CacheKeyGenerator.generate("findAll", null);
        Assertions.assertEquals("findAll", key);
    }

    @Test
    public void test_K02_methodWithEmptyParams_returnsNameOnly() {
        String key = CacheKeyGenerator.generate("findAll", new Object[] {});
        Assertions.assertEquals("findAll", key);
    }

    @Test
    public void test_K03_simpleParam_returnsStableHash() {
        String key1 = CacheKeyGenerator.generate("findById", new Object[] { "abc" });
        String key2 = CacheKeyGenerator.generate("findById", new Object[] { "abc" });

        Assertions.assertTrue(key1.startsWith("findById:"));
        Assertions.assertEquals(key1, key2, "Hash must be stable");
        // Length of SHA-256 hex is 64 + "findById:" prefix
        Assertions.assertEquals(9 + 64, key1.length());
    }

    @Test
    public void test_K04_multipleParams_isDeterministic() {
        String key1 = CacheKeyGenerator.generate("find", new Object[] { "a", 1, true });
        String key2 = CacheKeyGenerator.generate("find", new Object[] { "a", 1, true });
        Assertions.assertEquals(key1, key2);
    }

    @Test
    public void test_K05_nullParam_doesNotThrow() {
        String key = CacheKeyGenerator.generate("get", new Object[] { null });
        Assertions.assertTrue(key.startsWith("get:"));
    }

    @Test
    public void test_K06_mixOfNulls_isStableAndDistinct() {
        String key1 = CacheKeyGenerator.generate("get", new Object[] { null, "abc" });
        String key2 = CacheKeyGenerator.generate("get", new Object[] { null, "abc" });
        String key3 = CacheKeyGenerator.generate("get", new Object[] { "abc", null });

        Assertions.assertEquals(key1, key2);
        Assertions.assertNotEquals(key1, key3, "Order of nulls matters");
    }

    @Test
    public void test_K07_crossJvmStability_sha256Static() {
        // This confirms SHA-256 is used and is constant
        String key = CacheKeyGenerator.generate("m", new Object[] { "test" });
        // echo -n '["test"]' | sha256sum ->
        // ecfd160805b1b0481fd0793c745be3b45d2054582de1c4df5d9b8fa4d78e7fbc
        Assertions.assertEquals("m:ecfd160805b1b0481fd0793c745be3b45d2054582de1c4df5d9b8fa4d78e7fbc", key);
    }

    @Test
    public void test_K08_nonSerializableFallback_toString() {
        // An object with circular reference that Jackson cannot serialize by default
        class Circular {
            private final String val = "test";

            @Override
            public String toString() {
                return val;
            }

            @SuppressWarnings("unused")
            public Circular getSelf() {
                return this;
            }
        }

        String key = CacheKeyGenerator.generate("m", new Object[] { new Circular() });
        Assertions.assertTrue(key.startsWith("m:"), "Should fallback to toString() logic");
    }

    @Test
    public void test_K09_customToStringObject() {
        class Custom {
            @Override
            public String toString() {
                return "custom-val";
            }
        }
        String key = CacheKeyGenerator.generate("m", new Object[] { new Custom() });
        Assertions.assertTrue(key.contains(":"));
    }

    @Test
    public void test_K10_orderMatters() {
        String key1 = CacheKeyGenerator.generate("m", new Object[] { 1, 2 });
        String key2 = CacheKeyGenerator.generate("m", new Object[] { 2, 1 });
        Assertions.assertNotEquals(key1, key2);
    }

    @Test
    public void test_K11_complexObjects() {
        List<String> list = new ArrayList<>();
        list.add("item1");
        String key1 = CacheKeyGenerator.generate("m", new Object[] { list });

        list.add("item2");
        String key2 = CacheKeyGenerator.generate("m", new Object[] { list });

        Assertions.assertNotEquals(key1, key2);
    }

    @Test
    public void test_K12_differentMethodsSameParams_distinctKeys() {
        String key1 = CacheKeyGenerator.generate("methodA", new Object[] { "x" });
        String key2 = CacheKeyGenerator.generate("methodB", new Object[] { "x" });
        Assertions.assertNotEquals(key1, key2);
    }

    @Test
    public void test_K13_sha256Availability() {
        Assertions.assertDoesNotThrow(() -> CacheKeyGenerator.generate("test", new Object[] { "ping" }));
    }

    @Test
    public void test_M4_fallbackWithNullParamAndDefaultToString() {
        // Object that fails Jackson serialization AND uses default toString()
        // This tests both null-in-fallback AND the regex warning path for default
        // toString
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this; // circular reference
            }
            // No toString() override - uses default Object.toString() with @hexHash pattern
        };

        String key = CacheKeyGenerator.generate("m", new Object[] { null, unserializable });
        Assertions.assertTrue(key.startsWith("m:"));
        Assertions.assertTrue(key.contains("null"));
    }
}
