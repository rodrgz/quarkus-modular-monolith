package org.acme.invoicing.infra.client;

import org.acme.inventory.domain.api.dto.ProductDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

/**
 * REST Client for Inventory Microservice.
 * 
 * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 * ┃ REMOTE CALL TO INVENTORY MICROSERVICE ┃
 * ┃ ┃
 * ┃ When the inventory module is extracted to a microservice, ┃
 * ┃ we use this REST Client to make HTTP calls. ┃
 * ┃ ┃
 * ┃ Configured via: quarkus.rest-client.inventory-api.url ┃
 * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 */
@RegisterRestClient(configKey = "inventory-api")
@Path("/api/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface InventoryRestClient {

    /**
     * Finds multiple products by IDs.
     */
    @POST
    @Path("/products/bulk")
    List<ProductDTO> findByIds(List<String> productIds);

    /**
     * Lists all available products.
     */
    @GET
    @Path("/products")
    List<ProductDTO> listAvailable();

    /**
     * Finds a product by ID.
     */
    @GET
    @Path("/products/{id}")
    ProductDTO findById(@PathParam("id") String productId);
}
