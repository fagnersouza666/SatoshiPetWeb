package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenHasher;
import br.com.satoshipet.api.account.MagicLinkTokenRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MagicLinkVerifyResourceTest {

    @Inject
    MagicLinkTokenRepository tokenRepository;

    @Inject
    MagicLinkTokenHasher hasher;

    @Test
    void retorna401ParaTokenInexistente() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"token-que-nao-existe-no-banco\"}")
        .when()
                .post("/api/v1/auth/magic-link/verify")
        .then()
                .statusCode(401);
    }

    @Test
    void retorna401ParaBodyVazio() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
        .when()
                .post("/api/v1/auth/magic-link/verify")
        .then()
                .statusCode(401);
    }

    @Test
    void retorna200ComRegistrationRequiredTrueParaNovaContaVerify() {
        String rawToken = "verify-new-account-token-" + System.nanoTime();
        String email = "nova-conta-verify@example.com";
        inserirToken(email, rawToken, Instant.now(), Instant.now().plusSeconds(900));

        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + rawToken + "\"}")
        .when()
                .post("/api/v1/auth/magic-link/verify")
        .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("registrationRequired", equalTo(true))
                .body("verifiedEmail", equalTo(email));
    }

    @Test
    void retorna401ParaTokenExpirado() {
        String rawToken = "expired-token-" + System.nanoTime();
        // Token já expirado (expires_at no passado)
        inserirToken("expirado@example.com", rawToken,
                Instant.now().minusSeconds(1000), Instant.now().minusSeconds(100));

        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + rawToken + "\"}")
        .when()
                .post("/api/v1/auth/magic-link/verify")
        .then()
                .statusCode(401);
    }

    private void inserirToken(String email, String rawToken, Instant issuedAt, Instant expiresAt) {
        QuarkusTransaction.requiringNew().run(() -> {
            String tokenHash = hasher.hash(rawToken);
            MagicLinkToken token = MagicLinkToken.issue(email, tokenHash, issuedAt, expiresAt);
            tokenRepository.persist(token);
        });
    }
}
