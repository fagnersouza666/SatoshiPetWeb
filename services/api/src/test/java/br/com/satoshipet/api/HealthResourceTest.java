package br.com.satoshipet.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o contrato operacional do SmallRye Health (FUND-07.A2.T2).
 *
 * <p>O endpoint é público para sondas, mas nunca deve retornar credenciais ou
 * detalhes de conexão. A disponibilidade do banco é publicada pelo check de
 * readiness fornecido pelo Quarkus.</p>
 */
@QuarkusTest
class HealthResourceTest {

    @Test
    void endpointAgregadoRetornaSaudeSemDadosSensiveis() {
        Response response = health("/q/health");
        String payload = response.asString();

        assertAll(
                () -> response.then().statusCode(200),
                () -> response.then().contentType("application/json"),
                () -> response.then().body("status", equalTo("UP")),
                () -> response.then().body("checks", notNullValue()),
                () -> assertNoSensitiveInformation(payload));
    }

    @Test
    void readinessRetornaSaudeDasDependenciasSemConfiguracaoInterna() {
        Response response = health("/q/health/ready");
        List<?> checks = response.jsonPath().getList("checks");
        String payload = response.asString();

        assertAll(
                () -> response.then().statusCode(200),
                () -> response.then().body("status", equalTo("UP")),
                () -> assertTrue(checks != null && !checks.isEmpty()),
                () -> assertNoSensitiveInformation(payload));
    }

    private static Response health(String path) {
        return given()
                .accept("application/json")
                .when()
                .get(path);
    }

    private static void assertNoSensitiveInformation(String payload) {
        String normalized = payload.toLowerCase(Locale.ROOT);
        assertAll(
                () -> assertFalse(normalized.contains("password")),
                () -> assertFalse(normalized.contains("secret")),
                () -> assertFalse(normalized.contains("jdbc:")),
                () -> assertFalse(normalized.contains("username")));
    }
}
