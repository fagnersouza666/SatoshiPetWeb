package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetFeeding;
import br.com.satoshipet.api.pet.PetReferencePortion;
import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import br.com.satoshipet.api.support.bitcoin.BitcoinTransactionFixture;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes de integração para {@link PublicAddressResource}.
 *
 * <p>Valida que somente campos públicos são retornados (CA-009) e que
 * a resposta tem o formato esperado.</p>
 *
 * <p>O endereço de fixture é criado em {@link #setup()} (transação commitada)
 * e removido em {@link #teardown()} (transação commitada), garantindo que
 * a chamada REST veja dados persistidos.</p>
 */
@QuarkusTest
class PublicAddressResourceTest {

    static final String FIXTURE_ADDR = BitcoinTransactionFixture.ADDRESS.toLowerCase();

    @Inject
    StubBitcoinIndexer stub;

    @BeforeEach
    void setup() {
        stub.reset();
        QuarkusTransaction.requiringNew().run(() -> deletarEnderecoDeFixture(FIXTURE_ADDR));
        QuarkusTransaction.requiringNew().run(() -> {
            Address a = Address.create(FIXTURE_ADDR, Instant.now());
            a.persist();
        });
    }

    @AfterEach
    void teardown() {
        QuarkusTransaction.requiringNew().run(() -> deletarEnderecoDeFixture(FIXTURE_ADDR));
        stub.reset();
    }

    private void deletarEnderecoDeFixture(String canonical) {
        Address.findByCanonical(canonical).ifPresent(address -> {
            List<Account> contas = new ArrayList<>();
            Pet.findByAddress(address).ifPresent(pet -> {
                PetFeeding.delete("pet", pet);
                PetReferencePortion.delete("pet", pet);
                contas.add(pet.creatorAccount);
                pet.delete();
            });
            AccountAddressBinding.find("address", address).<AccountAddressBinding>list()
                    .forEach(binding -> {
                        contas.add(binding.account);
                        binding.delete();
                    });
            LogicalReceipt.find("address", address).list()
                    .forEach(r -> ((LogicalReceipt) r).delete());
            BitcoinTransaction.find("address", address).list()
                    .forEach(t -> ((BitcoinTransaction) t).delete());
            AddressMonitorState.findByAddress(address).ifPresent(state -> state.delete());
            address.delete();
            contas.stream()
                    .map(conta -> conta.id)
                    .distinct()
                    .forEach(id -> {
                        Account conta = Account.findById(id);
                        if (conta != null) {
                            conta.delete();
                        }
                    });
        });
    }

    // -------------------------------------------------------------------------
    // Endereço não monitorado → 404
    // -------------------------------------------------------------------------

    @Test
    void enderecoNaoMonitoradoRetorna404() {
        // Usa endereço válido mas que não está no banco
        given()
                .when().get("/api/v1/public/addresses/" + BitcoinTestAddresses.MAINNET_UNMONITORED)
                .then()
                .statusCode(404);
    }

    // -------------------------------------------------------------------------
    // Endereço inválido → 400
    // -------------------------------------------------------------------------

    @Test
    void enderecoInvalidoRetorna400() {
        given()
                .when().get("/api/v1/public/addresses/not-a-bitcoin-address")
                .then()
                .statusCode(400);
    }

    // -------------------------------------------------------------------------
    // Endereço monitorado → 200 com dados públicos
    // -------------------------------------------------------------------------

    @Test
    void enderecoMonitoradoRetorna200ComCamposPublicos() {
        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("address",       equalTo(FIXTURE_ADDR))
                .body("network",       notNullValue())
                .body("confirmedSats", notNullValue())
                .body("pendingSats",   notNullValue())
                .body("qrData",        startsWith("bitcoin:"))
                .body("explorerUrl",   notNullValue());
    }

    @Test
    void respostaNaoContemCamposPrivados() {
        String json = given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .extract().asString();

        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"accountId\""),
                "accountId não deve aparecer na resposta pública");
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"petId\""),
                "petId não deve aparecer na resposta pública");
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"bindingId\""),
                "bindingId não deve aparecer na resposta pública");
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"email\""),
                "email não deve aparecer na resposta pública");
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"logicalReceiptId\""),
                "logicalReceiptId não deve aparecer na resposta pública");
    }

    @Test
    void qrDataTemFormatoBitcoinUri() {
        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("qrData", equalTo("bitcoin:" + FIXTURE_ADDR));
    }

    @Test
    void explorerUrlContemEnderecoNaUrl() {
        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("explorerUrl", containsString(FIXTURE_ADDR));
    }

    @Test
    void historicoTransacoesVazioQuandoNaoHaTxs() {
        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("recentTransactions", hasSize(0))
                .body("transactionCount",   equalTo(0));
    }

    // -------------------------------------------------------------------------
    // Canonicalização de bech32 uppercase
    // -------------------------------------------------------------------------

    @Test
    void bech32UppercaseECanonicalizadoParaMinuscula() {
        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR.toUpperCase())
                .then()
                .statusCode(200)
                .body("address", equalTo(FIXTURE_ADDR));
    }

    // -------------------------------------------------------------------------
    // Network detection correta para endereço regtest
    // -------------------------------------------------------------------------

    @Test
    void regtestEnderecoRetornaNetworkRegtest() {
        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("network", equalTo("regtest"));
    }

    @Test
    void ovoOmitePetStateERotulaAguardandoReferencia() {
        persistirPetNoFixture("Pixel", pet -> { });

        io.restassured.path.json.JsonPath json = given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("petName", equalTo("Pixel"))
                .body("presentation", equalTo("EGG"))
                .body("awaitingReference", equalTo(true))
                .body("pendingMovesEgg", equalTo(false))
                .body("reserveHours", equalTo("0.0000000000"))
                .body("operationalLabel", equalTo("Aguardando referência do plano"))
                .extract().jsonPath();

        assertNull(json.get("petState"));
        String raw = given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .extract().asString();
        assertFalse(raw.contains("\"petId\""), "petId não deve aparecer na resposta pública");
        assertFalse(raw.contains("\"accountId\""), "accountId não deve aparecer na resposta pública");
        assertFalse(raw.contains("\"email\""), "email não deve aparecer na resposta pública");
    }

    @Test
    void ovoComEntradaPendenteRotulaRecebimentoPendente() {
        persistirPetNoFixture("Pixel", pet -> pet.awaitingReference = false);
        QuarkusTransaction.requiringNew().run(() -> {
            Address address = Address.findByCanonical(FIXTURE_ADDR).orElseThrow();
            LogicalReceipt.createPending(address, "ab".repeat(32), 1_000L, Instant.now()).persist();
        });

        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("presentation", equalTo("EGG"))
                .body("pendingMovesEgg", equalTo(true))
                .body("operationalLabel", equalTo("Recebimento pendente"));
    }

    @Test
    void ovoPreparandoNascimentoQuandoJaNasceuSemArteAprovada() {
        persistirPetNoFixture("Pixel", pet -> {
            pet.awaitingReference = false;
            pet.bornAt = Instant.parse("2026-09-12T12:00:00Z");
            pet.artworkStatus = ArtworkStatus.PENDING;
        });

        given()
                .accept(ContentType.JSON)
                .when().get("/api/v1/public/addresses/" + FIXTURE_ADDR)
                .then()
                .statusCode(200)
                .body("presentation", equalTo("EGG"))
                .body("pendingMovesEgg", equalTo(false))
                .body("operationalLabel", equalTo("Preparando nascimento"));
    }

    private void persistirPetNoFixture(String nome, java.util.function.Consumer<Pet> customize) {
        QuarkusTransaction.requiringNew().run(() -> {
            Address address = Address.findByCanonical(FIXTURE_ADDR).orElseThrow();
            Account account = Account.create(
                    "public-pet-" + System.nanoTime() + "@example.com",
                    "America/Sao_Paulo",
                    "pt-BR",
                    Instant.now());
            account.persist();
            AccountAddressBinding.create(account, address, true, Instant.now()).persist();
            Pet pet = Pet.create(address, account, nome, Instant.now());
            customize.accept(pet);
            pet.persist();
        });
    }
}
