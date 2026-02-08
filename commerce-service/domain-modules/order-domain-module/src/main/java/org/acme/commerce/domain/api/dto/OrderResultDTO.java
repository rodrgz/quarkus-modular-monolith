package org.acme.commerce.domain.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Calculation Result DTO.
 * 
 * Represents the final result after inventory validation and total calculation.
 */
public record OrderResultDTO(
    String orderId,
    String customerId,
    List<ItemDTO> items,
    BigDecimal totalValue,
    OrderStatus status,
    String message,
    LocalDateTime processedAt
) {
    
    public record ItemDTO(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        boolean inStock
    ) {}
    
    public enum OrderStatus {
        APPROVED,
        PARTIALLY_APPROVED,
        REJECTED_OUT_OF_STOCK,
        ERROR
    }
    
    /**
     * Factory method for approved order.
     */
    public static OrderResultDTO approved(String orderId, String customerId, 
                                            List<ItemDTO> items, BigDecimal totalValue) {
        return new OrderResultDTO(
            orderId, customerId, items, totalValue,
            OrderStatus.APPROVED, "Order approved successfully",
            LocalDateTime.now()
        );
    }
    
    /**
     * Factory method for order rejected due to lack of stock.
     */
    public static OrderResultDTO rejectedOutOfStock(String orderId, String customerId, 
                                                       List<ItemDTO> items) {
        return new OrderResultDTO(
            orderId, customerId, items, BigDecimal.ZERO,
            OrderStatus.REJECTED_OUT_OF_STOCK, "Order rejected: products out of stock",
            LocalDateTime.now()
        );
    }
}
