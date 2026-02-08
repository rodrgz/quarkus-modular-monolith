package org.acme.invoicing.infra.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration Tests for InvoiceResource.
 */
@QuarkusTest
class InvoiceResourceIT {

    @Test
    void should_return_health_ok() {
        given()
                .when()
                .get("/api/invoices/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"));
    }

    @Test
    void should_generate_invoice_successfully() {
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
                .post("/api/invoices")
                .then()
                .statusCode(200)
                .body("customerId", equalTo("CLI-001"))
                .body("items.size()", equalTo(2))
                .body("total", notNullValue());
    }

    @Test
    void should_reject_invoice_without_customer() {
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
                .post("/api/invoices")
                .then()
                .statusCode(400)
                .body("message", containsString("customerId"));
    }

    @Test
    void should_handle_empty_items() {
        String requestBody = """
                {
                    "customerId": "CLI-002",
                    "items": []
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/invoices")
                .then()
                .statusCode(200)
                .body("customerId", equalTo("CLI-002"))
                .body("items.size()", equalTo(0))
                .body("total", equalTo(0.0f));
    }
}
