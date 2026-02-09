package org.acme.scheduler;

import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class DistributedLockInterceptorTest {

    @Mock
    LockProvider lockProvider;

    @Mock
    InvocationContext context;

    @InjectMocks
    DistributedLockInterceptor interceptor;

    @BeforeEach
    void setup() {
        Mockito.when(context.getTarget()).thenReturn(new SampleService());
    }

    public static class SampleService {
        @DistributedLock(name = "test-job", lockAtMostFor = 10, lockAtLeastFor = 1)
        public String doWork() {
            return "ok";
        }

        @DistributedLock(name = "test-job")
        public String doWorkDefault() {
            return "ok";
        }
    }

    @Test
    public void test_DI01_lockAcquired_executesMethod() throws Exception {
        Method method = SampleService.class.getMethod("doWork");
        Mockito.when(context.getMethod()).thenReturn(method);
        Mockito.when(context.proceed()).thenReturn("ok");

        LockProvider.LockHandle mockHandle = Mockito.mock(LockProvider.LockHandle.class);
        Mockito.when(lockProvider.tryLock(Mockito.eq("test-job"), Mockito.any(Duration.class)))
                .thenReturn(Optional.of(mockHandle));

        Object result = interceptor.intercept(context);

        Assertions.assertEquals("ok", result);
        Mockito.verify(context).proceed();
        Mockito.verify(mockHandle).close();
    }

    @Test
    public void test_DI02_lockHeld_skipsExecution() throws Exception {
        Method method = SampleService.class.getMethod("doWork");
        Mockito.when(context.getMethod()).thenReturn(method);

        Mockito.when(lockProvider.tryLock(Mockito.eq("test-job"), Mockito.any(Duration.class)))
                .thenReturn(Optional.empty());

        Object result = interceptor.intercept(context);

        Assertions.assertNull(result);
        Mockito.verify(context, Mockito.never()).proceed();
    }

    @Test
    public void test_DI03_methodFailure_releasesLock() throws Exception {
        Method method = SampleService.class.getMethod("doWork");
        Mockito.when(context.getMethod()).thenReturn(method);
        Mockito.when(context.proceed()).thenThrow(new RuntimeException("fail"));

        LockProvider.LockHandle mockHandle = Mockito.mock(LockProvider.LockHandle.class);
        Mockito.when(lockProvider.tryLock(Mockito.eq("test-job"), Mockito.any(Duration.class)))
                .thenReturn(Optional.of(mockHandle));

        Assertions.assertThrows(RuntimeException.class, () -> interceptor.intercept(context));
        Mockito.verify(mockHandle).close();
    }
}
