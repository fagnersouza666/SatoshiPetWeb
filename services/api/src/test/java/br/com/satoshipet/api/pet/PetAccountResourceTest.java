package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenHasher;
import br.com.satoshipet.api.account.MagicLinkTokenRepository;
import br.com.satoshipet.api.btc.LogicalReceipt;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HTTP da fila de apresentação (PET-15). Sessão {@code sp_session} + CSRF no skip.
 */
@QuarkusTest
class PetAccountResourceTest {

    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;

    @Inject
    MagicLinkTokenRepository tokenRepository;

    @Inject
    MagicLinkTokenHasher hasher;

    @Inject
    PetLifecyclePort lifecycle;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    void filaSemSessaoRetorna401() {
        given()
                .when()
                .get("/api/v1/account/pet/presentation-queue")
                .then()
                .statusCode(401)
                .body("code", equalTo("unauthorized"));
    }

    @Test
    void skipSemSessaoRetorna401() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/account/pet/presentation/skip")
                .then()
                .statusCode(401)
                .body("code", equalTo("unauthorized"));
    }

    @Test
    void statsSemSessaoRetorna401() {
        given()
                .when()
                .get("/api/v1/account/pet/stats")
                .then()
                .statusCode(401)
                .body("code", equalTo("unauthorized"));
    }

    @Test
    void snapshotSemSessaoRetorna401() {
        given()
                .when()
                .get("/api/v1/account/pet")
                .then()
                .statusCode(401)
                .body("code", equalTo("unauthorized"));
    }

    @Test
    void snapshotAutenticadoRetornaBlocoCompartilhadoFilaEStats() {
        RegisteredAccount registered = registerUniqueAccount("pet-snap");

        io.restassured.path.json.JsonPath json = given()
                .cookie("sp_session", registered.sessionToken())
                .when()
                .get("/api/v1/account/pet")
                .then()
                .statusCode(200)
                .body("petName", equalTo("Pixel"))
                .body("presentation", equalTo("EGG"))
                .body("awaitingReference", equalTo(true))
                .body("pendingMovesEgg", equalTo(false))
                .body("operationalLabel", equalTo("Aguardando referência do plano"))
                .body("presentationQueue.items", notNullValue())
                .body("stats.timeInStateHours", notNullValue())
                .extract()
                .jsonPath();

        assertNull(json.get("petState"));
        String raw = given()
                .cookie("sp_session", registered.sessionToken())
                .when()
                .get("/api/v1/account/pet")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        assertFalse(raw.contains("\"petId\""), "petId não deve aparecer no snapshot autenticado");
        assertFalse(raw.contains("\"accountId\""), "accountId não deve aparecer no snapshot autenticado");
        assertFalse(raw.contains("\"email\""), "email não deve aparecer no snapshot autenticado");
    }

    @Test
    void duasContasNoMesmoEnderecoCompartilhamBlocoDoPet() {
        RegisteredAccount first = registerUniqueAccount("share-a");
        String address = QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.findById(UUID.fromString(first.accountId()));
            return AccountAddressBinding.findActivePrimary(account).orElseThrow().address.canonical;
        });
        RegisteredAccount second = registerAccountOnAddress("share-b", address, "OutroNome");

        io.restassured.path.json.JsonPath publicJson = given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/public/addresses/" + address)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        io.restassured.path.json.JsonPath petA = given()
                .cookie("sp_session", first.sessionToken())
                .when()
                .get("/api/v1/account/pet")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        io.restassured.path.json.JsonPath petB = given()
                .cookie("sp_session", second.sessionToken())
                .when()
                .get("/api/v1/account/pet")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        assertEquals("Pixel", publicJson.getString("petName"));
        for (String field : List.of(
                "petName",
                "presentation",
                "petState",
                "reserveHours",
                "awaitingReference",
                "pendingMovesEgg",
                "operationalLabel")) {
            Object expected = publicJson.get(field);
            assertEquals(expected, petA.get(field), field + " conta A");
            assertEquals(expected, petB.get(field), field + " conta B");
        }
    }

    @Test
    void ca034HttpTresComemoracoesAposRegistro() {
        RegisteredAccount registered = registerUniqueAccount("ca034-http");
        feedLiveReceipts(registered.accountId(), 3);

        given()
                .cookie("sp_session", registered.sessionToken())
                .when()
                .get("/api/v1/account/pet/presentation-queue")
                .then()
                .statusCode(200)
                .body("items", hasSize(3))
                .body("items[0].eventType", equalTo("PET_FEEDING_APPLIED"))
                .body("items[0].amountSats", equalTo((int) FIVE_THOUSAND))
                .body("items[0].eventId", notNullValue());
    }

    @Test
    void ca035HttpSkipEsvaziaFilaSemAlterarReserva() {
        RegisteredAccount registered = registerUniqueAccount("ca035-http");
        feedLiveReceipts(registered.accountId(), 2);
        BigDecimal reserveBefore = readReserve(registered.accountId());

        given()
                .cookie("sp_session", registered.sessionToken())
                .header("X-CSRF-Token", registered.csrfToken())
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/account/pet/presentation/skip")
                .then()
                .statusCode(200)
                .body("skipped", equalTo(true));

        given()
                .cookie("sp_session", registered.sessionToken())
                .when()
                .get("/api/v1/account/pet/presentation-queue")
                .then()
                .statusCode(200)
                .body("items", hasSize(0));

        assertEquals(0, readReserve(registered.accountId()).compareTo(reserveBefore));
    }

    private RegisteredAccount registerUniqueAccount(String marker) {
        return registerAccountOnAddress(marker, uniqueMainnetAddress(), "Pixel");
    }

    private RegisteredAccount registerAccountOnAddress(String marker, String address, String petName) {
        String rawToken = marker + "-token-" + System.nanoTime();
        String email = marker + "-" + System.nanoTime() + "@example.com";
        inserirToken(email, rawToken);

        ExtractableResponse<Response> response = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "token": "%s",
                            "bitcoinAddress": "%s",
                            "petName": "%s"
                        }
                        """.formatted(rawToken, address, petName))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .cookie("sp_session", notNullValue())
                .header("X-CSRF-Token", notNullValue())
                .extract();

        return new RegisteredAccount(
                response.path("accountId"),
                response.cookie("sp_session"),
                response.header("X-CSRF-Token")
        );
    }

    private void feedLiveReceipts(String accountId, int count) {
        QuarkusTransaction.requiringNew().run(() -> {
            Account account = Account.findById(UUID.fromString(accountId));
            AccountAddressBinding binding = AccountAddressBinding.findActivePrimary(account).orElseThrow();
            Pet pet = Pet.findByAddress(binding.address).orElseThrow();
            portionPort.recordPositivePortion(
                    pet.id, account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, Instant.now());
            Instant observedAt = Instant.now();
            for (int i = 0; i < count; i++) {
                LogicalReceipt receipt = LogicalReceipt.createPending(
                        pet.address,
                        UUID.randomUUID().toString().replace("-", ""),
                        FIVE_THOUSAND,
                        observedAt
                );
                receipt.persist();
                lifecycle.onReceiptObserved(pet.id, receipt.id, FIVE_THOUSAND, true, observedAt);
            }
        });
    }

    private BigDecimal readReserve(String accountId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.findById(UUID.fromString(accountId));
            AccountAddressBinding binding = AccountAddressBinding.findActivePrimary(account).orElseThrow();
            Pet pet = Pet.findByAddress(binding.address).orElseThrow();
            return pet.reserveHours;
        });
    }

    private void inserirToken(String email, String rawToken) {
        QuarkusTransaction.requiringNew().run(() -> {
            String tokenHash = hasher.hash(rawToken);
            MagicLinkToken token = MagicLinkToken.issue(
                    email, tokenHash, Instant.now(), Instant.now().plusSeconds(900));
            tokenRepository.persist(token);
        });
    }

    private static String uniqueMainnetAddress() {
        byte[] priv = new byte[32];
        new SecureRandom().nextBytes(priv);
        if (priv[0] == 0) {
            priv[0] = 1;
        }
        return org.bitcoinj.base.Address.fromKey(
                MainNetParams.get(),
                ECKey.fromPrivate(priv),
                ScriptType.P2WPKH
        ).toString();
    }

    private record RegisteredAccount(String accountId, String sessionToken, String csrfToken) {
    }
}
