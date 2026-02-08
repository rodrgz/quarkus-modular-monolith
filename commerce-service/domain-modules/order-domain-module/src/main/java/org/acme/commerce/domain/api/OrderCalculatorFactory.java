package org.acme.commerce.domain.api;

import org.acme.commerce.domain.internal.OrderCalculatorImpl;

/**
 * Factory for creating OrderCalculator instances.
 * 
 * This factory is part of the PUBLIC API and allows creating OrderCalculator
 * instances without directly importing the internal implementation class.
 * 
 * Usage in CDI producers:
 * <pre>
 * &#64;Produces
 * &#64;ApplicationScoped
 * public OrderCalculator orderCalculator(InventoryGateway inventoryGateway) {
 *     return OrderCalculatorFactory.create(inventoryGateway);
 * }
 * </pre>
 */
public final class OrderCalculatorFactory {

    private OrderCalculatorFactory() {
        // Utility class
    }

    /**
     * Creates an OrderCalculator with the given InventoryGateway.
     *
     * @param inventoryGateway the port for inventory operations
     * @return a new OrderCalculator instance
     */
    public static OrderCalculator create(InventoryGateway inventoryGateway) {
        return new OrderCalculatorImpl(inventoryGateway);
    }
}
