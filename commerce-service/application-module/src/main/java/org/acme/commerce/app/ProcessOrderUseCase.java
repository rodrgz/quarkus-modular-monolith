package org.acme.commerce.app;

import org.acme.commerce.domain.api.OrderCalculator;
import org.acme.commerce.domain.api.OrderCalculator.OrderItem;
import org.acme.commerce.domain.api.dto.OrderResultDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Main Use Case: Process Order.
 * 
 * This is the entry point of the application layer.
 * Orchestrates business flow calling domain services.
 * 
 * Responsibilities:
 * - Receive request in application format
 * - Convert to domain format
 * - Call domain services
 * - Return result
 */
@ApplicationScoped
public class ProcessOrderUseCase {

    private final OrderCalculator orderCalculator;

    @Inject
    public ProcessOrderUseCase(OrderCalculator orderCalculator) {
        this.orderCalculator = orderCalculator;
    }

    /**
     * Process an order.
     *
     * @param request Order data
     * @return Processing result
     */
    public OrderResultDTO execute(ProcessOrderRequest request) {
        // Convert request to domain format
        List<OrderItem> domainItems = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity()))
                .toList();

        // Delegate to domain
        return orderCalculator.process(request.customerId(), domainItems);
    }

    /**
     * Request to process order.
     */
    public record ProcessOrderRequest(
            String customerId,
            List<OrderItemRequest> items) {
        public record OrderItemRequest(String productId, int quantity) {
        }
    }
}
