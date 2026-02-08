package org.acme.order.infra.rest;

import org.acme.order.app.ProcessOrderUseCase;
import org.acme.order.app.ProcessOrderUseCase.ProcessOrderRequest;
import org.acme.order.domain.api.dto.OrderResultDTO;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST Resource for order operations.
 * 
 * This is the HTTP entry point of the service.
 * Responsibilities:
 * - Receive HTTP requests
 * - Convert to application format
 * - Delegate to Use Cases
 * - Format HTTP response
 */
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final ProcessOrderUseCase processOrderUseCase;

    @Inject
    public OrderResource(ProcessOrderUseCase processOrderUseCase) {
        this.processOrderUseCase = processOrderUseCase;
    }

    /**
     * Process a new order.
     * 
     * POST /api/orders
     */
    @POST
    public Response processOrder(ProcessOrderRequest request) {
        if (request == null || request.customerId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("customerId is required"))
                    .build();
        }

        OrderResultDTO result = processOrderUseCase.execute(request);

        return switch (result.status()) {
            case APPROVED, PARTIALLY_APPROVED -> Response.ok(result).build();
            case REJECTED_OUT_OF_STOCK -> Response.status(Response.Status.CONFLICT)
                    .entity(result).build();
            case ERROR -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(result).build();
        };
    }

    /**
     * Simple health check for tests.
     * 
     * GET /api/orders/health
     */
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(new HealthResponse("ok", "Order Service is working")).build();
    }

    // Response DTOs
    record ErrorResponse(String message) {
    }

    record HealthResponse(String status, String message) {
    }
}
