package org.acme.invoicing.domain.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generated Invoice DTO.
 */
public record InvoiceDTO(
    String invoiceId,
    String customerId,
    List<InvoiceItemDTO> items,
    BigDecimal subtotal,
    BigDecimal taxes,
    BigDecimal total,
    LocalDateTime generatedAt,
    InvoiceStatus status
) {
    public record InvoiceItemDTO(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal itemValue
    ) {}
    
    public enum InvoiceStatus {
        PENDING,
        ISSUED,
        PAID,
        CANCELLED
    }
}
