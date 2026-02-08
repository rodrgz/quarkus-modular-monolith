package org.acme.order.domain.internal;

import org.acme.order.domain.api.InventoryGateway;
import org.acme.order.domain.api.InventoryGateway.AvailabilityDTO;
import org.acme.order.domain.api.OrderCalculator;
import org.acme.order.domain.api.dto.OrderResultDTO;
import org.acme.order.domain.api.dto.OrderResultDTO.ItemDTO;
import org.acme.order.domain.api.dto.OrderResultDTO.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of Order Calculator.
 * 
 * This class contains pure business logic:
 * - Consults stock via Port (InventoryGateway)
 * - Validates availability of each item
 * - Calculates totals
 * - Determines order status
 * 
 * Note that this class does NOT have framework annotations (CDI, JPA).
 * InventoryGateway injection is done via constructor.
 */
public class OrderCalculatorImpl implements OrderCalculator {

    private final InventoryGateway inventoryGateway;

    public OrderCalculatorImpl(InventoryGateway inventoryGateway) {
        this.inventoryGateway = inventoryGateway;
    }

    @Override
    public OrderResultDTO process(String customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return createErrorResult(customerId, "Order without items");
        }

        // 1. Check availability in stock
        List<String> productIds = items.stream()
            .map(OrderItem::productId)
            .toList();
        
        List<AvailabilityDTO> availabilities = inventoryGateway.checkAvailability(productIds);
        
        // 2. Map availabilities by productId for quick access
        Map<String, AvailabilityDTO> availMap = availabilities.stream()
            .collect(Collectors.toMap(AvailabilityDTO::productId, Function.identity()));

        // 3. Process each item
        List<ItemDTO> processedItems = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        boolean allAvailable = true;
        boolean someAvailable = false;

        for (OrderItem item : items) {
            AvailabilityDTO avail = availMap.get(item.productId());
            
            if (avail == null) {
                // Product not found
                processedItems.add(new ItemDTO(
                    item.productId(), "Product not found",
                    item.quantity(), BigDecimal.ZERO, BigDecimal.ZERO, false
                ));
                allAvailable = false;
                continue;
            }

            boolean hasStock = avail.hasStockFor(item.quantity());
            BigDecimal subtotal = hasStock 
                ? avail.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                : BigDecimal.ZERO;

            processedItems.add(new ItemDTO(
                item.productId(),
                avail.productName(),
                item.quantity(),
                avail.unitPrice(),
                subtotal,
                hasStock
            ));

            if (hasStock) {
                totalValue = totalValue.add(subtotal);
                someAvailable = true;
            } else {
                allAvailable = false;
            }
        }

        // 4. Determine final status
        String orderId = UUID.randomUUID().toString();
        OrderStatus status;
        String message;

        if (allAvailable) {
            status = OrderStatus.APPROVED;
            message = "Order approved successfully";
        } else if (someAvailable) {
            status = OrderStatus.PARTIALLY_APPROVED;
            message = "Order partially approved - some items out of stock";
        } else {
            status = OrderStatus.REJECTED_OUT_OF_STOCK;
            message = "Order rejected - no items available in stock";
        }

        return new OrderResultDTO(
            orderId, customerId, processedItems, totalValue,
            status, message, LocalDateTime.now()
        );
    }

    private OrderResultDTO createErrorResult(String customerId, String message) {
        return new OrderResultDTO(
            UUID.randomUUID().toString(), customerId, 
            List.of(), BigDecimal.ZERO,
            OrderStatus.ERROR, message, LocalDateTime.now()
        );
    }
}
