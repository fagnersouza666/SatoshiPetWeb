package br.com.satoshipet.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/** Verifica que a API não delega dados a origens fora do allowlist. */
@QuarkusTest
class CorsOriginTest {

    private static final String ORIGEM_PWA = "http://localhost:4200";
    private static final String ORIGEM_NAO_AUTORIZADA = "https://atacante.example";

    @Test
    void rejeitaRequisicaoDeOrigemNaoAutorizada() {
        given()
                .header("Origin", ORIGEM_NAO_AUTORIZADA)
        .when()
                .get("/api/v1/hello")
        .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void rejeitaPreflightDeOrigemNaoAutorizada() {
        given()
                .header("Origin", ORIGEM_NAO_AUTORIZADA)
                .header("Access-Control-Request-Method", "GET")
        .when()
                .options("/api/v1/hello")
        .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void aceitaOrigemPwaConfiguradaSemRefletirOrigemArbitraria() {
        given()
                .header("Origin", ORIGEM_PWA)
        .when()
                .get("/api/v1/hello")
        .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo(ORIGEM_PWA))
                .header("Access-Control-Allow-Credentials", equalTo("true"));
    }
}
