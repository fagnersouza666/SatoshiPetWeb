package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenHasher;
import br.com.satoshipet.api.account.MagicLinkTokenRepository;
import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
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
class RegistrationResourceTest {

    @Inject
    MagicLinkTokenRepository tokenRepository;

    @Inject
    MagicLinkTokenHasher hasher;

    // Endereço Bitcoin mainnet válido (P2WPKH)
    private static final String VALID_ADDRESS = BitcoinTestAddresses.MAINNET_BECH32;

    @Test
    void registraNovaContaComSucesso() {
        String rawToken = "reg-success-token-" + System.nanoTime();
        String email = "reg-success-" + System.nanoTime() + "@example.com";
        inserirToken(email, rawToken);

        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "token": "%s",
                        "bitcoinAddress": "%s",
                        "petName": "Satoshi"
                    }
                    """.formatted(rawToken, VALID_ADDRESS))
        .when()
                .post("/api/v1/auth/register")
        .then()
                .statusCode(201)
                .body("status", equalTo("ok"))
                .body("accountId", notNullValue())
                .cookie("sp_session", notNullValue())
                .header("X-CSRF-Token", notNullValue());
    }

    @Test
    void retorna422ParaTokenInvalido() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "token": "token-invalido-inexistente",
                        "bitcoinAddress": "%s",
                        "petName": "Satoshi"
                    }
                    """.formatted(VALID_ADDRESS))
        .when()
                .post("/api/v1/auth/register")
        .then()
                .statusCode(422)
                .body("code", equalTo("token_invalid"));
    }

    @Test
    void retorna422ParaEnderecoInvalido() {
        String rawToken = "reg-invalid-addr-" + System.nanoTime();
        String email = "reg-invalid-addr-" + System.nanoTime() + "@example.com";
        inserirToken(email, rawToken);

        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "token": "%s",
                        "bitcoinAddress": "nao-e-um-endereco",
                        "petName": "Satoshi"
                    }
                    """.formatted(rawToken))
        .when()
                .post("/api/v1/auth/register")
        .then()
                .statusCode(422)
                .body("code", equalTo("invalid_address"));
    }

    @Test
    void retorna400ParaBodySemToken() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"bitcoinAddress\":\"" + VALID_ADDRESS + "\",\"petName\":\"Satoshi\"}")
        .when()
                .post("/api/v1/auth/register")
        .then()
                .statusCode(400);
    }

    private void inserirToken(String email, String rawToken) {
        QuarkusTransaction.requiringNew().run(() -> {
            String tokenHash = hasher.hash(rawToken);
            MagicLinkToken token = MagicLinkToken.issue(
                    email, tokenHash, Instant.now(), Instant.now().plusSeconds(900));
            tokenRepository.persist(token);
        });
    }
}
