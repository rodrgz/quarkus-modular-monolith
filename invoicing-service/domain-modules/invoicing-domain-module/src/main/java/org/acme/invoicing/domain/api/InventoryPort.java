package org.acme.invoicing.domain.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port (interface) for inventory product lookup.
 * 
 * This interface is used by the invoicing domain to fetch product information
 * without depending on the concrete InventoryService implementation.
 * 
 * Adapters will implement this interface:
 * - LocalAdapter: delegates to InventoryService in-memory
 * - RemoteAdapter: makes HTTP call to inventory-microservice
 */
public interface InventoryPort {

    /**
     * Find products by their IDs.
     *
     * @param productIds List of product IDs to find
     * @return List of products found
     */
    List<ProductInfo> findProductsByIds(List<String> productIds);

    /**
     * Product information record.
     */
    record ProductInfo(
        String productId,
        String name,
        BigDecimal price
    ) {}
}
