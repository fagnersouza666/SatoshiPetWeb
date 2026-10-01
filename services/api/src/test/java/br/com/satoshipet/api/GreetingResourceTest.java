package br.com.satoshipet.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class GreetingResourceTest {

    @Test
    void helloEndpointIsAvailable() {
        given()
                .when().get("/api/v1/hello")
                .then()
                .statusCode(200)
                .body(is("Olá do Satoshi Pet Web"));
    }

    @Test
    void preservaCorrelationIdRecebidoEDevolveNoHeader() {
        given()
                .header("X-Correlation-Id", "request-123")
                .when().get("/api/v1/hello")
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", is("request-123"));
    }

    @Test
    void geraCorrelationIdQuandoHeaderNaoFoiInformado() {
        String correlationId = given()
                .when().get("/api/v1/hello")
                .then()
                .statusCode(200)
                .extract().header("X-Correlation-Id");

        assertNotNull(correlationId);
        assertEquals(36, correlationId.length());
        assertNotNull(UUID.fromString(correlationId));
    }

    @Test
    void naoRepropagaHeaderMaiorQueLimiteDoOutbox() {
        String correlationId = given()
                .header("X-Correlation-Id", "x".repeat(37))
                .when().get("/api/v1/hello")
                .then()
                .statusCode(200)
                .extract().header("X-Correlation-Id");

        assertNotNull(correlationId);
        assertNotEquals("x".repeat(37), correlationId);
        assertEquals(36, correlationId.length());
    }
}
