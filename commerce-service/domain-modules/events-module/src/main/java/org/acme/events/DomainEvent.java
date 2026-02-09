package org.acme.events;

import java.time.Instant;

/**
 * Base interface for all domain events.
 * 
 * All events in the system should implement this interface to ensure
 * consistent event structure and traceability.
 */
public interface DomainEvent {
    
    /**
     * Unique identifier for the event.
     */
    String eventId();
    
    /**
     * Type of the event (e.g., "OrderCreated", "InventoryReserved").
     */
    String eventType();
    
    /**
     * Timestamp when the event occurred.
     */
    Instant occurredAt();
    
    /**
     * Aggregate root ID that generated the event.
     */
    String aggregateId();
    
    /**
     * Version of the event schema (for evolution).
     */
    default int schemaVersion() {
        return 1;
    }
}
