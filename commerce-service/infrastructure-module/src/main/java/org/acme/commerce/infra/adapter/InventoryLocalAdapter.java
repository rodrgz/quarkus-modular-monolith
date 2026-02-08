package org.acme.commerce.infra.adapter;

import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.dto.ProductDTO;
import org.acme.commerce.domain.api.InventoryGateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * LOCAL Adapter for inventory inquiry.
 * 
 * This implementation makes IN-MEMORY calls directly to InventoryService.
 * It is the default implementation when running as a monolith.
 * 
 * When the inventory module is extracted to a microservice,
 * this adapter will be replaced by RemoteAdapter via @Priority.
 */
@ApplicationScoped
public class InventoryLocalAdapter implements InventoryGateway {

    private static final Logger LOG = Logger.getLogger(InventoryLocalAdapter.class);

    private final InventoryService inventoryService;

    @Inject
    public InventoryLocalAdapter(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public List<AvailabilityDTO> checkAvailability(List<String> productIds) {
        LOG.infof("🏠 [LOCAL] Checking availability for %d products", productIds.size());

        List<ProductDTO> products = inventoryService.findByIds(productIds);

        return products.stream()
                .map(p -> new AvailabilityDTO(
                        p.productId(),
                        p.name(),
                        p.quantityAvailable(),
                        p.price(),
                        p.isAvailable()))
                .toList();
    }
}
