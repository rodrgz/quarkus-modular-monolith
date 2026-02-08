package org.acme.invoicing.domain.internal;

import org.acme.invoicing.domain.api.InventoryPort;
import org.acme.invoicing.domain.api.InventoryPort.ProductInfo;
import org.acme.invoicing.domain.api.InvoiceService;
import org.acme.invoicing.domain.api.dto.InvoiceDTO;
import org.acme.invoicing.domain.api.dto.InvoiceDTO.InvoiceItemDTO;
import org.acme.invoicing.domain.api.dto.InvoiceDTO.InvoiceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of the Invoicing Service.
 * 
 * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 * ┃  PORTS AND ADAPTERS ARCHITECTURE                                   ┃
 * ┃                                                                    ┃
 * ┃  This class depends on InventoryPort (Port interface).             ┃
 * ┃  The adapter implementation is injected by the infrastructure.     ┃
 * ┃                                                                    ┃
 * ┃  This allows switching between LocalAdapter and RemoteAdapter      ┃
 * ┃  without changing any business logic.                              ┃
 * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 */
public class InvoiceServiceImpl implements InvoiceService {

    // Tax rate for example
    private static final BigDecimal TAX_RATE = new BigDecimal("0.15"); // 15%
    
    // Port for inventory operations (injected via constructor)
    private final InventoryPort inventoryPort;

    public InvoiceServiceImpl(InventoryPort inventoryPort) {
        this.inventoryPort = inventoryPort;
    }

    @Override
    public InvoiceDTO generateInvoice(String customerId, List<InvoiceItemRequest> items) {
        // 1. Fetch products from inventory via Port
        List<String> productIds = items.stream()
            .map(InvoiceItemRequest::productId)
            .toList();
        
        List<ProductInfo> products = inventoryPort.findProductsByIds(productIds);
        
        // 2. Map for quick access
        Map<String, ProductInfo> productMap = products.stream()
            .collect(Collectors.toMap(ProductInfo::productId, Function.identity()));

        // 3. Calculate invoice items
        List<InvoiceItemDTO> invoiceItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (InvoiceItemRequest item : items) {
            ProductInfo product = productMap.get(item.productId());
            
            if (product != null) {
                BigDecimal itemValue = product.price()
                    .multiply(BigDecimal.valueOf(item.quantity()));
                
                invoiceItems.add(new InvoiceItemDTO(
                    product.productId(),
                    product.name(),
                    item.quantity(),
                    product.price(),
                    itemValue
                ));
                
                subtotal = subtotal.add(itemValue);
            }
        }

        // 4. Calculate taxes
        BigDecimal taxes = subtotal.multiply(TAX_RATE)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxes);

        // 5. Generate invoice
        return new InvoiceDTO(
            "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            customerId,
            invoiceItems,
            subtotal,
            taxes,
            total,
            LocalDateTime.now(),
            InvoiceStatus.ISSUED
        );
    }
}
