package org.acme.inventory.domain.api;

import org.acme.inventory.domain.internal.InventoryServiceImpl;
import org.acme.inventory.domain.api.dto.ProductDTO;

import java.util.Map;

/**
 * Factory for creating InventoryService instances.
 * 
 * This factory is part of the PUBLIC API and allows creating InventoryService
 * instances without directly importing the internal implementation class.
 * 
 * Usage in CDI producers:
 * <pre>
 * &#64;Produces
 * &#64;ApplicationScoped
 * public InventoryService inventoryService() {
 *     return InventoryServiceFactory.createDefault();
 * }
 * </pre>
 */
public final class InventoryServiceFactory {

    private InventoryServiceFactory() {
        // Utility class
    }

    /**
     * Creates a default InventoryService with pre-loaded sample data.
     *
     * @return a new InventoryService instance
     */
    public static InventoryService createDefault() {
        return new InventoryServiceImpl();
    }

    /**
     * Creates an InventoryService with custom initial data.
     * Useful for testing scenarios.
     *
     * @param initialProducts map of productId to ProductDTO
     * @return a new InventoryService instance with the provided data
     */
    public static InventoryService create(Map<String, ProductDTO> initialProducts) {
        return new InventoryServiceImpl(initialProducts);
    }
}
