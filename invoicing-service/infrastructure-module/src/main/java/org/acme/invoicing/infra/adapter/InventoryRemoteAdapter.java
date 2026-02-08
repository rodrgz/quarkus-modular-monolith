package org.acme.invoicing.infra.adapter;

import org.acme.inventory.domain.api.dto.ProductDTO;
import org.acme.invoicing.domain.api.InventoryPort;
import org.acme.invoicing.infra.client.InventoryRestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * REMOTE Adapter for InventoryPort.
 * 
 * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 * ┃ DEMONSTRATION: MODULE EXTRACTION ┃
 * ┃ ┃
 * ┃ This adapter replaces in-memory call with HTTP call. ┃
 * ┃ The business logic (InvoiceServiceImpl) remains UNCHANGED! ┃
 * ┃ ┃
 * ┃ To activate: ┃
 * ┃ mvn quarkus:dev -Dquarkus.profile=remote ┃
 * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 */
@ApplicationScoped
@Alternative
public class InventoryRemoteAdapter implements InventoryPort {

    private static final Logger LOG = Logger.getLogger(InventoryRemoteAdapter.class);

    @Inject
    @RestClient
    InventoryRestClient inventoryClient;

    @Override
    @Retry(maxRetries = 3, delay = 200, retryOn = RuntimeException.class)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "findProductsByIdsFallback")
    public List<ProductInfo> findProductsByIds(List<String> productIds) {
        LOG.infof("🌐 [REMOTE] Fetching %d products via HTTP", productIds.size());

        List<ProductDTO> products = inventoryClient.findByIds(productIds);

        return products.stream()
                .map(p -> new ProductInfo(p.productId(), p.name(), p.price()))
                .toList();
    }

    // ==================== FALLBACK ====================

    public List<ProductInfo> findProductsByIdsFallback(List<String> productIds) {
        LOG.warnf("⚠️ [FALLBACK] Inventory microservice unavailable for %d products", productIds.size());
        return Collections.emptyList();
    }
}
