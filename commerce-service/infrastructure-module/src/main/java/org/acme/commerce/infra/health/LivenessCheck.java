package org.acme.commerce.infra.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Liveness Check - Verifies if the application is "live".
 * 
 * Used by Kubernetes to determine if the pod should be restarted.
 * A simple liveness check returns UP if the application is running.
 * 
 * Endpoint: GET /q/health/live
 */
@Liveness
@ApplicationScoped
public class LivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("commerce-service-live")
                .up()
                .withData("service", "Order Service")
                .withData("version", "1.0.0-SNAPSHOT")
                .build();
    }
}
