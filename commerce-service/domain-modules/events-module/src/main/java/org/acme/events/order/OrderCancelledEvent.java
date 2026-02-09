package org.acme.events.order;

import org.acme.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an order is cancelled.
 */
public record OrderCancelledEvent(
    String eventId,
    String orderId,
    String customerId,
    String reason,
    Instant occurredAt
) implements DomainEvent {
    
    public OrderCancelledEvent(String orderId, String customerId, String reason) {
        this(
            UUID.randomUUID().toString(),
            orderId,
            customerId,
            reason,
            Instant.now()
        );
    }
    
    @Override
    public String eventType() {
        return "OrderCancelled";
    }
    
    @Override
    public String aggregateId() {
        return orderId;
    }
}
