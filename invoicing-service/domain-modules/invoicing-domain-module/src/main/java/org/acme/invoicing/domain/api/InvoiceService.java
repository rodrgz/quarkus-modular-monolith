package org.acme.invoicing.domain.api;

import org.acme.invoicing.domain.api.dto.InvoiceDTO;
import java.util.List;

/**
 * Invoicing Service Interface.
 */
public interface InvoiceService {

    /**
     * Generates an invoice for a customer based on products.
     *
     * @param customerId Customer ID
     * @param items List of items (productId, quantity)
     * @return Generated invoice
     */
    InvoiceDTO generateInvoice(String customerId, List<InvoiceItemRequest> items);
    
    record InvoiceItemRequest(String productId, int quantity) {}
}
