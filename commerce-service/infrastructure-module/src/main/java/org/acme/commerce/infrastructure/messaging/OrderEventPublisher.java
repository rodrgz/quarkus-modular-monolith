package org.acme.commerce.infrastructure.messaging;

import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.events.order.OrderCreatedEvent;
import org.acme.events.order.OrderCancelledEvent;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Publisher for order domain events to Kafka.
 * 
 * <h2>Topics:</h2>
 * <ul>
 *   <li>{@code orders-out} → {@link OrderCreatedEvent}</li>
 *   <li>{@code orders-cancelled-out} → {@link OrderCancelledEvent}</li>
 * </ul>
 */
@ApplicationScoped
public class OrderEventPublisher {
    
    private static final Logger LOG = Logger.getLogger(OrderEventPublisher.class);
    
    @Inject
    @Channel("orders-out")
    Emitter<Record<String, OrderCreatedEvent>> orderCreatedEmitter;
    
    @Inject
    @Channel("orders-cancelled-out")
    Emitter<Record<String, OrderCancelledEvent>> orderCancelledEmitter;
    
    /**
     * Publish order created event.
     * 
     * @param event The order created event
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        LOG.infof("Publishing OrderCreatedEvent: orderId=%s, customerId=%s", 
            event.orderId(), event.customerId());
        
        // Use orderId as Kafka key for partitioning
        orderCreatedEmitter.send(Record.of(event.orderId(), event));
    }
    
    /**
     * Publish order cancelled event.
     * 
     * @param event The order cancelled event
     */
    public void publishOrderCancelled(OrderCancelledEvent event) {
        LOG.infof("Publishing OrderCancelledEvent: orderId=%s, reason=%s", 
            event.orderId(), event.reason());
        
        orderCancelledEmitter.send(Record.of(event.orderId(), event));
    }
}
