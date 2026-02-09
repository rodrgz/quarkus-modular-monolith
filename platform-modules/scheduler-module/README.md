# Scheduler Module

A distributed scheduling extension for Quarkus that ensures scheduled tasks run exactly once across a cluster of microservices using Redis-based distributed locks.

## Overview

Standard `@Scheduled` methods in Quarkus run on *every* instance of the application. In a clustered environment (e.g., Kubernetes), this leads to duplicate execution. 

This module provides the `@DistributedLock` annotation to ensure that only one instance executes the task at a time.

## Key Features

- **Distributed Locking**: Uses Redis `SETNX` to acquire locks.
- **Automatic Expiration**: Locks have a maximum duration (`lockAtMostFor`) to prevent deadlocks if a pod crashes.
- **Minimum Execution Time**: Prevents re-execution within a short window (`lockAtLeastFor`) to handle clock skew or rapid failures.
- **Non-Blocking**: If a lock is held by another instance, the task execution is silently skipped.

## Usage

Simply add `@DistributedLock` to your `@Scheduled` method.

```java
import io.quarkus.scheduler.Scheduled;
import org.acme.scheduler.DistributedLock;

@ApplicationScoped
public class DailyReportJob {

    @Scheduled(cron = "0 0 2 * * ?") // Runs at 2:00 AM
    @DistributedLock(
        name = "daily-report-job", 
        lockAtMostFor = 3600,      // Lock expires after 1 hour (safety net)
        lockAtLeastFor = 60        // Keep lock for at least 1 min
    )
    public void generateReport() {
        // This code runs on only ONE pod
        reportService.generate();
    }
}
```

## Configuration

### Instance Identity

The module needs to identify the current instance to manage lock ownership. By default, it generates a random UUID on startup. For consistent identification across restarts (optional), you can configure:

```properties
# application.properties
scheduler.instance-id=${POD_NAME:default-instance}
```

### Redis Configuration

The module relies on the Quarkus Redis client.

```properties
quarkus.redis.hosts=redis://localhost:6379
```

## Annotation Reference

### `@DistributedLock`

| Attribute | Default | Description |
|-----------|---------|-------------|
| `name` | **Required** | Unique name for the lock key in Redis (`lock:{name}`). |
| `lockAtMostFor` | `300` (5m) | **Safety Stop**: The maximum time (seconds) to hold the lock. If the task hangs or the pod crashes, the lock is released after this time. |
| `lockAtLeastFor` | `60` (1m) | **Debounce**: The minimum time (seconds) the lock remains held, even if the task finishes instantly. Useful to prevent double-execution due to clock differences between pods. |

## How it Works

1. **Trigger**: Quarkus Scheduler triggers the method on all pods.
2. **Interceptor**: The `@DistributedLock` interceptor intercepts the call.
3. **Acquire**: Attempts to set a Redis key `lock:{name}` with `NX` (Not Exists).
   - **Success**: The current pod owns the lock. Proceed with execution.
   - **Failure**: Another pod owns the lock. Skip execution silently.
4. **Release**:
   - Once execution finishes (and `lockAtLeastFor` time has passed), the lock is released.
   - If execution fails (exception), the lock is released immediately.
