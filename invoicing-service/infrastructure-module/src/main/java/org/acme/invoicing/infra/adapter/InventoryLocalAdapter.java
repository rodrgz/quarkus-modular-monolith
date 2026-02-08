package org.acme.invoicing.infra.adapter;

import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.dto.ProductDTO;
import org.acme.invoicing.domain.api.InventoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * LOCAL Adapter for InventoryPort.
 * 
 * This adapter delegates to the in-memory InventoryService implementation.
 * Used when invoicing-service runs as part of the monolith.
 * 
 * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 * ┃ DEMONSTRATION: PORTS AND ADAPTERS ┃
 * ┃ ┃
 * ┃ This is the default adapter (in-memory, fast). ┃
 * ┃ The RemoteAdapter can replace it when profile=remote is active. ┃
 * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 */
@ApplicationScoped
public class InventoryLocalAdapter implements InventoryPort {

    private static final Logger LOG = Logger.getLogger(InventoryLocalAdapter.class);

    @Inject
    InventoryService inventoryService;

    @Override
    public List<ProductInfo> findProductsByIds(List<String> productIds) {
        LOG.infof("🏠 [LOCAL] Fetching %d products from in-memory inventory", productIds.size());

        List<ProductDTO> products = inventoryService.findByIds(productIds);

        return products.stream()
                .map(p -> new ProductInfo(p.productId(), p.name(), p.price()))
                .toList();
    }
}
