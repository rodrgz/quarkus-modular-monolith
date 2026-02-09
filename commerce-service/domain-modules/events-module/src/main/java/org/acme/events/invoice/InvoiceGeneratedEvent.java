package org.acme.events.invoice;

import org.acme.events.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an invoice is generated.
 */
public record InvoiceGeneratedEvent(
    String eventId,
    String invoiceId,
    String orderId,
    String customerId,
    BigDecimal totalAmount,
    Instant occurredAt
) implements DomainEvent {
    
    public InvoiceGeneratedEvent(String invoiceId, String orderId, String customerId, BigDecimal totalAmount) {
        this(
            UUID.randomUUID().toString(),
            invoiceId,
            orderId,
            customerId,
            totalAmount,
            Instant.now()
        );
    }
    
    @Override
    public String eventType() {
        return "InvoiceGenerated";
    }
    
    @Override
    public String aggregateId() {
        return invoiceId;
    }
}
