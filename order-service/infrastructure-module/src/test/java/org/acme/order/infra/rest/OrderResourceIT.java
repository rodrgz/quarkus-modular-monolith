package org.acme.order.infra.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration Tests for OrderResource.
 */
@QuarkusTest
class OrderResourceIT {

    @Test
    void should_return_health_ok() {
        given()
                .when()
                .get("/api/orders/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"));
    }

    @Test
    void should_process_order_successfully() {
        String requestBody = """
                {
                    "customerId": "CLI-001",
                    "items": [
                        {"productId": "PROD-001", "quantity": 1},
                        {"productId": "PROD-002", "quantity": 2}
                    ]
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(200)
                .body("customerId", equalTo("CLI-001"))
                .body("status", anyOf(equalTo("APPROVED"), equalTo("PARTIALLY_APPROVED")))
                .body("items.size()", greaterThan(0));
    }

    @Test
    void should_reject_order_without_customer() {
        String requestBody = """
                {
                    "items": [
                        {"productId": "PROD-001", "quantity": 1}
                    ]
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(400);
    }

    @Test
    void should_handle_out_of_stock_product() {
        // PROD-005 is configured without stock in InventoryServiceImpl
        String requestBody = """
                {
                    "customerId": "CLI-002",
                    "items": [
                        {"productId": "PROD-005", "quantity": 1}
                    ]
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(409))) // PARTIALLY_APPROVED or REJECTED
                .body("customerId", equalTo("CLI-002"));
    }
}
