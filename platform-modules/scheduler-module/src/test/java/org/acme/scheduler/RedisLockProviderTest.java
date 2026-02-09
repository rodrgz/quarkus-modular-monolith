package org.acme.scheduler;

import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class RedisLockProviderTest {

    @Mock
    RedisDataSource redisDataSource;

    RedisLockProvider lockProvider;

    @BeforeEach
    void setup() {
        lockProvider = new RedisLockProvider(redisDataSource, "test-instance");
    }

    @Test
    public void test_RL01_lockAvailable_acquiresSuccessfully() {
        String lockName = "test-lock";

        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(mockResponse.toString()).thenReturn("1");

        Mockito.doReturn(mockResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.anyString(), Mockito.eq("1"), Mockito.anyString(),
                Mockito.eq("test-instance"), Mockito.anyString());

        Optional<LockProvider.LockHandle> handle = lockProvider.tryLock(lockName, Duration.ofSeconds(60));

        Assertions.assertTrue(handle.isPresent());
        Mockito.verify(redisDataSource).execute(Mockito.eq("EVAL"), Mockito.contains("SETNX"), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void test_RL02_lockHeld_returnsEmpty() {
        String lockName = "busy-lock";

        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(mockResponse.toString()).thenReturn("0");

        Mockito.doReturn(mockResponse).when(redisDataSource).execute(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());

        Optional<LockProvider.LockHandle> handle = lockProvider.tryLock(lockName, Duration.ofSeconds(60));

        Assertions.assertTrue(handle.isEmpty());
    }

    @Test
    public void test_RL03_redisDownDuringAcquisition_returnsEmpty() {
        String lockName = "fail-lock";

        Mockito.doThrow(new RuntimeException("Redis connection failed")).when(redisDataSource).execute(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());

        Optional<LockProvider.LockHandle> handle = lockProvider.tryLock(lockName, Duration.ofSeconds(60));

        Assertions.assertTrue(handle.isEmpty());
    }

    @Test
    public void test_LH01_releaseNormal_executesLuaScript() throws Exception {
        String lockName = "to-release";
        String redisKey = "lock:" + lockName;

        Response okResponse = Mockito.mock(Response.class);
        Mockito.when(okResponse.toString()).thenReturn("1");

        Mockito.doReturn(okResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.contains("SETNX"), Mockito.eq("1"), Mockito.eq(redisKey),
                Mockito.eq("test-instance"), Mockito.anyString());

        LockProvider.LockHandle handle = lockProvider.tryLock(lockName, Duration.ofSeconds(60)).get();

        Response releaseResponse = Mockito.mock(Response.class);
        Mockito.when(releaseResponse.toString()).thenReturn("1");

        Mockito.doReturn(releaseResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.contains("DEL"), Mockito.eq("1"), Mockito.eq(redisKey),
                Mockito.eq("test-instance"));

        handle.close();

        Mockito.verify(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.contains("DEL"), Mockito.eq("1"), Mockito.eq(redisKey),
                Mockito.eq("test-instance"));
    }

    @Test
    public void test_LH02_releaseNonOwned_doesNotDelete() throws Exception {
        String lockName = "stolen-lock";
        String redisKey = "lock:" + lockName;

        Response okResponse = Mockito.mock(Response.class);
        Mockito.when(okResponse.toString()).thenReturn("1");

        Mockito.doReturn(okResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.contains("SETNX"), Mockito.eq("1"), Mockito.eq(redisKey),
                Mockito.eq("test-instance"), Mockito.anyString());

        LockProvider.LockHandle handle = lockProvider.tryLock(lockName, Duration.ofSeconds(60)).get();

        Response releaseResponse = Mockito.mock(Response.class);
        Mockito.when(releaseResponse.toString()).thenReturn("0");

        Mockito.doReturn(releaseResponse).when(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.contains("DEL"), Mockito.eq("1"), Mockito.eq(redisKey),
                Mockito.eq("test-instance"));

        handle.close();

        Mockito.verify(redisDataSource).execute(
                Mockito.eq("EVAL"), Mockito.contains("DEL"), Mockito.eq("1"), Mockito.eq(redisKey),
                Mockito.eq("test-instance"));
    }
}
