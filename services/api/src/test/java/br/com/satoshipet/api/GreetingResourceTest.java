package br.com.satoshipet.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

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
}
