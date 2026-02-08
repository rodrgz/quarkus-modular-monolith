package org.acme.order.domain.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port (interface) for inventory inquiry.
 * 
 * This interface will be implemented by Adapters in the infrastructure layer:
 * - LocalAdapter: in-memory call to InventoryService
 * - RemoteAdapter: HTTP call to an external service
 * 
 * The adapter switch is transparent to the domain.
 */
public interface InventoryGateway {

    /**
     * Check availability of products in stock.
     *
     * @param productIds List of product IDs to check
     * @return List of availability by product
     */
    List<AvailabilityDTO> checkAvailability(List<String> productIds);
    
    /**
     * Represents the availability of a product.
     */
    record AvailabilityDTO(
        String productId,
        String productName,
        int quantityAvailable,
        BigDecimal unitPrice,
        boolean available
    ) {
        public boolean hasStockFor(int quantityRequested) {
            return available && quantityAvailable >= quantityRequested;
        }
    }
}
