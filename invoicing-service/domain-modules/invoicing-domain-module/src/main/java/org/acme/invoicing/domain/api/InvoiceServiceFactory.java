package org.acme.invoicing.domain.api;

import org.acme.invoicing.domain.internal.InvoiceServiceImpl;

/**
 * Factory for creating InvoiceService instances.
 * 
 * This factory is part of the PUBLIC API and allows creating InvoiceService
 * instances without directly importing the internal implementation class.
 * 
 * Usage in CDI producers:
 * <pre>
 * &#64;Produces
 * &#64;ApplicationScoped
 * public InvoiceService invoiceService(InventoryPort inventoryPort) {
 *     return InvoiceServiceFactory.create(inventoryPort);
 * }
 * </pre>
 */
public final class InvoiceServiceFactory {

    private InvoiceServiceFactory() {
        // Utility class
    }

    /**
     * Creates an InvoiceService with the given InventoryPort.
     *
     * @param inventoryPort the port for inventory operations
     * @return a new InvoiceService instance
     */
    public static InvoiceService create(InventoryPort inventoryPort) {
        return new InvoiceServiceImpl(inventoryPort);
    }
}
