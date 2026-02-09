package org.acme.scheduler;

import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Assertions;
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
public class LockAtLeastForTest {

    @Mock
    LockProvider lockProvider;

    @Mock
    InvocationContext context;

    @InjectMocks
    DistributedLockInterceptor interceptor;

    public static class SampleService {
        @DistributedLock(name = "long-lock", lockAtMostFor = 10, lockAtLeastFor = 2)
        public void failQuickly() {
            throw new RuntimeException("instant-fail");
        }
    }

    @Test
    public void test_LI05_exception_shouldStillHonorLockAtLeastFor() throws Exception {
        Method method = SampleService.class.getMethod("failQuickly");
        Mockito.when(context.getMethod()).thenReturn(method);
        Mockito.when(context.proceed()).thenThrow(new RuntimeException("instant-fail"));
        Mockito.when(context.getTarget()).thenReturn(new SampleService());

        LockProvider.LockHandle mockHandle = Mockito.mock(LockProvider.LockHandle.class);
        Mockito.when(lockProvider.tryLock(Mockito.eq("long-lock"), Mockito.any(Duration.class)))
                .thenReturn(Optional.of(mockHandle));

        long start = System.currentTimeMillis();

        Assertions.assertThrows(RuntimeException.class, () -> interceptor.intercept(context));

        long duration = System.currentTimeMillis() - start;

        // If bug exists, duration will be < 2000ms (likely < 100ms)
        // If fixed, duration should be >= 2000ms
        Assertions.assertTrue(duration >= 2000,
                "Lock should be held for at least 2 seconds even on exception. Actual duration: " + duration + "ms");

        Mockito.verify(mockHandle).close();
    }
}
