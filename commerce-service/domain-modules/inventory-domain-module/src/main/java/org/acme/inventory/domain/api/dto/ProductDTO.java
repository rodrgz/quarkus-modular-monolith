package org.acme.inventory.domain.api.dto;

import java.math.BigDecimal;

/**
 * Inventory Product DTO.
 */
public record ProductDTO(
    String productId,
    String name,
    String description,
    BigDecimal price,
    int quantityAvailable,
    String category
) {
    public boolean isAvailable() {
        return quantityAvailable > 0;
    }
    
    public boolean hasStockFor(int quantity) {
        return quantityAvailable >= quantity;
    }
}
