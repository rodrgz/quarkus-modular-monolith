package org.acme.order.infra.health;

import org.acme.inventory.domain.api.InventoryService;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Readiness Check - Verifies if the application is ready to receive requests.
 * 
 * Used by Kubernetes to determine if the pod can receive traffic.
 * A readiness check verifies critical dependencies (DB, external services).
 * 
 * Endpoint: GET /q/health/ready
 */
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private final InventoryService inventoryService;

    @Inject
    public ReadinessCheck(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("order-service-ready");

        try {
            // Checks if inventory service is available
            var availableProducts = inventoryService.listAvailable();

            builder.up()
                    .withData("inventory.status", "available")
                    .withData("inventory.active_products", availableProducts.size());

        } catch (Exception e) {
            builder.down()
                    .withData("inventory.status", "unavailable")
                    .withData("inventory.error", e.getMessage());
        }

        return builder.build();
    }
}
