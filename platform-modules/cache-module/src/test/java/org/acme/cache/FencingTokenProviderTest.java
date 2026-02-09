package org.acme.cache;

import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FencingTokenProviderTest {

    @Mock
    RedisDataSource redisDataSource;

    FencingTokenProvider provider;

    @BeforeEach
    void setup() {
        provider = new FencingTokenProvider(redisDataSource);
    }

    @Test
    public void test_FT03_validateAndSetToken_valid_callsEVAL() {
        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(mockResponse.toString()).thenReturn("1");

        Mockito.doReturn(mockResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.anyString(), Mockito.eq("1"), Mockito.anyString(), Mockito.anyString());

        boolean valid = provider.validateAndSetToken("cache", "key", 100);

        Assertions.assertTrue(valid);
        Mockito.verify(redisDataSource).execute(Mockito.eq("EVAL"), Mockito.contains("tonumber"), Mockito.eq("1"),
                Mockito.contains("fence:cache:key"), Mockito.eq("100"));
    }

    @Test
    public void test_FT04_validateAndSetToken_stale_returnsFalse() {
        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(mockResponse.toString()).thenReturn("0");

        Mockito.doReturn(mockResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.anyString(), Mockito.eq("1"), Mockito.anyString(), Mockito.anyString());

        boolean valid = provider.validateAndSetToken("cache", "key", 50);

        Assertions.assertFalse(valid);
    }

    @Test
    public void test_FT06_validateAndSetToken_redisDown_failOpen() {
        Mockito.doThrow(new RuntimeException("Redis disconnected")).when(redisDataSource).execute(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());

        // Should return true (fail-open) as per implementation
        boolean valid = provider.validateAndSetToken("cache", "key", 100);

        Assertions.assertTrue(valid);
    }
}
