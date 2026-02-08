package org.acme.commerce.domain.api;

import org.acme.commerce.domain.api.dto.OrderResultDTO;
import java.util.List;

/**
 * Order Calculator Interface.
 * 
 * This is the public API of the order domain module.
 */
public interface OrderCalculator {

    /**
     * Process an order, validating stock and calculating totals.
     *
     * @param customerId Customer ID
     * @param items List of order items (productId, quantity)
     * @return Result of processing with status and totals
     */
    OrderResultDTO process(String customerId, List<OrderItem> items);
    
    /**
     * Represents an order item to be processed.
     */
    record OrderItem(String productId, int quantity) {}
}
