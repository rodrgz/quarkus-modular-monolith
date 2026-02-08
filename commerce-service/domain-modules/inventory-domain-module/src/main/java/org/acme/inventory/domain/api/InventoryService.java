package org.acme.inventory.domain.api;

import org.acme.inventory.domain.api.dto.ProductDTO;
import java.util.List;
import java.util.Optional;

/**
 * Inventory Service Interface.
 * 
 * This is the public API of the inventory domain module.
 */
public interface InventoryService {

    /**
     * Find a product by ID.
     *
     * @param productId Product ID
     * @return Found product or empty
     */
    Optional<ProductDTO> findById(String productId);
    
    /**
     * Find multiple products by IDs.
     *
     * @param productIds List of product IDs
     * @return List of found products
     */
    List<ProductDTO> findByIds(List<String> productIds);
    
    /**
     * List all available products.
     *
     * @return List of products with stock > 0
     */
    List<ProductDTO> listAvailable();
    
    /**
     * Reserve quantity of a product (decrements stock).
     *
     * @param productId Product ID
     * @param quantity Quantity to reserve
     * @return true if reservation was successful
     */
    boolean reserve(String productId, int quantity);
    
    /**
     * Release reservation of a product (increments stock).
     *
     * @param productId Product ID
     * @param quantity Quantity to release
     */
    void release(String productId, int quantity);
}
