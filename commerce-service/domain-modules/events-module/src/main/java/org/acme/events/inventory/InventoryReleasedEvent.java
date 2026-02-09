package org.acme.events.inventory;

import org.acme.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when inventory reservation is released (e.g., order cancelled).
 */
public record InventoryReleasedEvent(
    String eventId,
    String productId,
    String orderId,
    int quantityReleased,
    int newStock,
    Instant occurredAt
) implements DomainEvent {
    
    public InventoryReleasedEvent(String productId, String orderId, int quantityReleased, int newStock) {
        this(
            UUID.randomUUID().toString(),
            productId,
            orderId,
            quantityReleased,
            newStock,
            Instant.now()
        );
    }
    
    @Override
    public String eventType() {
        return "InventoryReleased";
    }
    
    @Override
    public String aggregateId() {
        return productId;
    }
}
