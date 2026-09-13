package br.com.satoshipet.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class MetricsResourceTest {

    @Test
    void exponeMetricasPrometheusNoCaminhoConfigurado() {
        given()
                .when().get("/q/metrics")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("openmetrics-text"))
                .body(containsString("# HELP"), containsString("# TYPE"));
    }
}
