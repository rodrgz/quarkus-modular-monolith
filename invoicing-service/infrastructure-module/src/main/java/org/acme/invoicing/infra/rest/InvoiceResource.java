package org.acme.invoicing.infra.rest;

import org.acme.invoicing.app.GenerateInvoiceUseCase;
import org.acme.invoicing.app.GenerateInvoiceUseCase.GenerateInvoiceRequest;
import org.acme.invoicing.domain.api.dto.InvoiceDTO;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST Resource for invoicing operations.
 */
@Path("/api/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    private final GenerateInvoiceUseCase generateInvoiceUseCase;

    @Inject
    public InvoiceResource(GenerateInvoiceUseCase generateInvoiceUseCase) {
        this.generateInvoiceUseCase = generateInvoiceUseCase;
    }

    /**
     * Generates a new invoice.
     * 
     * POST /api/invoices
     */
    @POST
    public Response generateInvoice(GenerateInvoiceRequest request) {
        if (request == null || request.customerId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("customerId is required"))
                    .build();
        }

        InvoiceDTO invoice = generateInvoiceUseCase.execute(request);
        return Response.ok(invoice).build();
    }

    /**
     * Health check.
     */
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(new HealthResponse("ok", "Invoicing Service is working")).build();
    }

    record ErrorResponse(String message) {
    }

    record HealthResponse(String status, String message) {
    }
}
