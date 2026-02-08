package org.acme.commerce.infra.config;

import org.acme.commerce.domain.api.InventoryGateway;
import org.acme.commerce.domain.api.OrderCalculator;
import org.acme.commerce.domain.api.OrderCalculatorFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI Configuration for domain bean production.
 * 
 * This class "wiring" domain implementations (which don't have CDI annotations)
 * to Quarkus CDI container using @Produces.
 * 
 * Advantages of this approach:
 * - Domain remains pure and testable without CDI
 * - Centralized injection configuration
 * - Facilitates swapping implementations
 * - Uses factories to avoid importing internal packages
 */
@ApplicationScoped
public class DomainBeanProducer {

    /**
     * Produces OrderCalculator.
     * 
     * Note that we inject InventoryGateway (Port), not the concrete implementation.
     * CDI automatically resolves to LocalAdapter or RemoteAdapter.
     */
    @Produces
    @ApplicationScoped
    public OrderCalculator orderCalculator(InventoryGateway inventoryGateway) {
        return OrderCalculatorFactory.create(inventoryGateway);
    }
}
