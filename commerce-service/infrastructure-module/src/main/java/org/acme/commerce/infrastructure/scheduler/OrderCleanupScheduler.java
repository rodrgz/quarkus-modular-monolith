package org.acme.commerce.infrastructure.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.scheduler.DistributedLock;
import org.jboss.logging.Logger;

/**
 * Example scheduled tasks with distributed lock support.
 * 
 * <p>In a Kubernetes environment with multiple pods, only ONE pod
 * will execute the scheduled tasks thanks to the @DistributedLock annotation.</p>
 */
@ApplicationScoped
public class OrderCleanupScheduler {
    
    private static final Logger LOG = Logger.getLogger(OrderCleanupScheduler.class);
    
    /**
     * Daily cleanup of stale orders.
     * 
     * <p>Runs at 2 AM and holds the lock for maximum 1 hour.
     * Only one pod in the cluster will execute this task.</p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @DistributedLock(name = "order-daily-cleanup", lockAtMostFor = 3600, lockAtLeastFor = 60)
    public void cleanupStaleOrders() {
        LOG.info("Starting daily cleanup of stale orders...");
        
        // Example: Clean up orders older than 30 days that are still pending
        // orderService.cleanupStalePendingOrders(Duration.ofDays(30));
        
        LOG.info("Daily order cleanup completed");
    }
    
    /**
     * Hourly report generation.
     * 
     * <p>Runs every hour on the hour.
     * Lock duration is 5 minutes maximum.</p>
     */
    @Scheduled(cron = "0 0 * * * ?")
    @DistributedLock(name = "order-hourly-stats", lockAtMostFor = 300, lockAtLeastFor = 30)
    public void generateHourlyStats() {
        LOG.info("Generating hourly order statistics...");
        
        // Example: Generate order statistics for dashboards
        // metricsService.recordOrderStats();
        
        LOG.info("Hourly stats generation completed");
    }
    
    /**
     * Every 5 minutes check for orders requiring attention.
     * 
     * <p>This is a more frequent task that checks for orders
     * that may need manual intervention.</p>
     */
    @Scheduled(every = "5m")
    @DistributedLock(name = "order-attention-check", lockAtMostFor = 120, lockAtLeastFor = 30)
    public void checkOrdersRequiringAttention() {
        LOG.debug("Checking for orders requiring attention...");
        
        // Example: Find orders stuck in processing
        // alertService.checkStuckOrders();
    }
}
