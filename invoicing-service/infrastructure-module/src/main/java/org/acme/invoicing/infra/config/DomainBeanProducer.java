package org.acme.invoicing.infra.config;

import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.InventoryServiceFactory;
import org.acme.invoicing.domain.api.InventoryPort;
import org.acme.invoicing.domain.api.InvoiceService;
import org.acme.invoicing.domain.api.InvoiceServiceFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI Configuration for bean production.
 * 
 * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 * ┃ PORTS AND ADAPTERS PATTERN ┃
 * ┃ ┃
 * ┃ InvoiceService depends on InventoryPort (Port interface). ┃
 * ┃ The port is implemented by LocalAdapter or RemoteAdapter. ┃
 * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 */
@ApplicationScoped
public class DomainBeanProducer {

    /**
     * Produces InventoryService for the LocalAdapter.
     */
    @Produces
    @ApplicationScoped
    public InventoryService inventoryService() {
        return InventoryServiceFactory.createDefault();
    }

    /**
     * Produces InvoiceService using the InventoryPort.
     * 
     * CDI automatically resolves InventoryPort to:
     * - InventoryLocalAdapter (default)
     * - InventoryRemoteAdapter (when @Alternative is activated)
     */
    @Produces
    @ApplicationScoped
    public InvoiceService invoiceService(InventoryPort inventoryPort) {
        return InvoiceServiceFactory.create(inventoryPort);
    }
}
