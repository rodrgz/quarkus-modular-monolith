package org.acme.invoicing.app;

import org.acme.invoicing.domain.api.InvoiceService;
import org.acme.invoicing.domain.api.InvoiceService.InvoiceItemRequest;
import org.acme.invoicing.domain.api.dto.InvoiceDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Use Case: Generate Invoice.
 */
@ApplicationScoped
public class GenerateInvoiceUseCase {

    private final InvoiceService invoiceService;

    @Inject
    public GenerateInvoiceUseCase(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    public InvoiceDTO execute(GenerateInvoiceRequest request) {
        List<InvoiceItemRequest> items = request.items().stream()
                .map(i -> new InvoiceItemRequest(i.productId(), i.quantity()))
                .toList();

        return invoiceService.generateInvoice(request.customerId(), items);
    }

    public record GenerateInvoiceRequest(
            String customerId,
            List<ItemRequest> items) {
        public record ItemRequest(String productId, int quantity) {
        }
    }
}
