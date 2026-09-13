package br.com.satoshipet.api.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MagicLinkResourceTest {

    @Test
    void retorna202ParaEmailValido() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"pessoa@example.com\"}")
        .when()
                .post("/api/v1/auth/magic-link")
        .then()
                .statusCode(202)
                .body("status", equalTo("accepted"))
                .body("message", notNullValue());
    }

    @Test
    void retorna202ParaEmailNovoCasoNaoExista() {
        // Resposta uniforme — não revela se a conta existe
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"novo-usuario-inexistente@example.com\"}")
        .when()
                .post("/api/v1/auth/magic-link")
        .then()
                .statusCode(202)
                .body("status", equalTo("accepted"));
    }

    @Test
    void retorna400ParaEmailInvalido() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"nao-e-um-email\"}")
        .when()
                .post("/api/v1/auth/magic-link")
        .then()
                .statusCode(400)
                .body("code", equalTo("invalid_request"))
                .body("fieldErrors", notNullValue());
    }

    @Test
    void retorna400ParaEmailVazio() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"\"}")
        .when()
                .post("/api/v1/auth/magic-link")
        .then()
                .statusCode(400)
                .body("code", equalTo("invalid_request"));
    }

    @Test
    void retorna400ParaBodyNulo() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
        .when()
                .post("/api/v1/auth/magic-link")
        .then()
                .statusCode(400);
    }

    @Test
    void respostaNaoContemTokenOuSegredo() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"seguro@example.com\"}")
        .when()
                .post("/api/v1/auth/magic-link")
        .then()
                .statusCode(202)
                .body("token", equalTo(null))
                .body("expiresAt", equalTo(null))
                .body("link", equalTo(null));
    }
}
