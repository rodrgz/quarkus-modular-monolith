package org.acme.inventory.microservice.rest;

import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.dto.ProductDTO;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * REST Resource exposing the inventory-domain-module via HTTP.
 * 
 * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 * ┃ INVENTORY REST API (for remote calls) ┃
 * ┃ ┃
 * ┃ Endpoints: ┃
 * ┃ GET /api/inventory/products - List available ┃
 * ┃ GET /api/inventory/products/{id} - Find by ID ┃
 * ┃ POST /api/inventory/products/bulk - Find multiple by IDs ┃
 * ┃ POST /api/inventory/reserve - Reserve stock ┃
 * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 */
@Path("/api/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryResource {

    private final InventoryService inventoryService;

    @Inject
    public InventoryResource(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * List all available products.
     * 
     * GET /api/inventory/products
     */
    @GET
    @Path("/products")
    public Response listAvailable() {
        List<ProductDTO> products = inventoryService.listAvailable();
        return Response.ok(products).build();
    }

    /**
     * Find a product by ID.
     * 
     * GET /api/inventory/products/{id}
     */
    @GET
    @Path("/products/{id}")
    public Response findById(@PathParam("id") String productId) {
        return inventoryService.findById(productId)
                .map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Product not found: " + productId))
                        .build());
    }

    /**
     * Find multiple products by IDs.
     * 
     * POST /api/inventory/products/bulk
     * Body: ["PROD-001", "PROD-002"]
     */
    @POST
    @Path("/products/bulk")
    public Response findByIds(List<String> productIds) {
        List<ProductDTO> products = inventoryService.findByIds(productIds);
        return Response.ok(products).build();
    }

    /**
     * Reserve quantity of a product.
     * 
     * POST /api/inventory/reserve
     */
    @POST
    @Path("/reserve")
    public Response reserve(ReservationRequest request) {
        boolean success = inventoryService.reserve(request.productId(), request.quantity());

        if (success) {
            return Response.ok(new ReservationResponse(true, "Reservation successful")).build();
        } else {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ReservationResponse(false, "Insufficient stock"))
                    .build();
        }
    }

    /**
     * Health check.
     */
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(new HealthResponse("ok", "Inventory Microservice is working")).build();
    }

    // DTOs
    record ErrorResponse(String message) {
    }

    record HealthResponse(String status, String message) {
    }

    record ReservationRequest(String productId, int quantity) {
    }

    record ReservationResponse(boolean success, String message) {
    }
}
