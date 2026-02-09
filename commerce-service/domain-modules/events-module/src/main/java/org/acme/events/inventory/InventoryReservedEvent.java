package org.acme.events.inventory;

import org.acme.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when inventory is successfully reserved for an order.
 */
public record InventoryReservedEvent(
    String eventId,
    String productId,
    String orderId,
    int quantityReserved,
    int remainingStock,
    Instant occurredAt
) implements DomainEvent {
    
    public InventoryReservedEvent(String productId, String orderId, int quantityReserved, int remainingStock) {
        this(
            UUID.randomUUID().toString(),
            productId,
            orderId,
            quantityReserved,
            remainingStock,
            Instant.now()
        );
    }
    
    @Override
    public String eventType() {
        return "InventoryReserved";
    }
    
    @Override
    public String aggregateId() {
        return productId;
    }
}
