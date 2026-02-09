package org.acme.events.order;

import org.acme.events.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event emitted when an order is successfully created.
 */
public record OrderCreatedEvent(
    String eventId,
    String orderId,
    String customerId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    Instant occurredAt
) implements DomainEvent {
    
    public OrderCreatedEvent(String orderId, String customerId, List<OrderItem> items, BigDecimal totalAmount) {
        this(
            UUID.randomUUID().toString(),
            orderId,
            customerId,
            items,
            totalAmount,
            Instant.now()
        );
    }
    
    @Override
    public String eventType() {
        return "OrderCreated";
    }
    
    @Override
    public String aggregateId() {
        return orderId;
    }
    
    /**
     * Order item included in the event.
     */
    public record OrderItem(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
    ) {}
}
