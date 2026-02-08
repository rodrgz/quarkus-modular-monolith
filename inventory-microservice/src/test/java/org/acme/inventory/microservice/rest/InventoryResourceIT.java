package org.acme.inventory.microservice.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration Tests for InventoryResource.
 */
@QuarkusTest
class InventoryResourceIT {

    @Test
    void should_return_health_ok() {
        given()
                .when()
                .get("/api/inventory/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"));
    }

    @Test
    void should_list_all_products() {
        given()
                .when()
                .get("/api/inventory/products")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].productId", notNullValue())
                .body("[0].name", notNullValue());
    }

    @Test
    void should_find_product_by_id() {
        given()
                .when()
                .get("/api/inventory/products/{id}", "PROD-001")
                .then()
                .statusCode(200)
                .body("productId", equalTo("PROD-001"))
                .body("name", notNullValue())
                .body("price", notNullValue());
    }

    @Test
    void should_return_404_when_product_not_found() {
        given()
                .when()
                .get("/api/inventory/products/{id}", "PROD-UNKNOWN")
                .then()
                .statusCode(404)
                .body("message", containsString("not found"));
    }

    @Test
    void should_find_products_by_bulk_ids() {
        String requestBody = """
                ["PROD-001", "PROD-002"]
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/inventory/products/bulk")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("productId", hasItems("PROD-001", "PROD-002"));
    }

    @Test
    void should_reserve_stock_successfully() {
        String requestBody = """
                {
                    "productId": "PROD-001",
                    "quantity": 1
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/inventory/reserve")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("message", containsString("successful"));
    }

    @Test
    void should_fail_reservation_when_insufficient_stock() {
        // PROD-005 has 0 stock according to README
        String requestBody = """
                {
                    "productId": "PROD-005",
                    "quantity": 1
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/inventory/reserve")
                .then()
                .statusCode(409)
                .body("success", equalTo(false))
                .body("message", containsString("Insufficient"));
    }
}
