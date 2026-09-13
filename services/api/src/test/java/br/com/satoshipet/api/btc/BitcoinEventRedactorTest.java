package br.com.satoshipet.api.btc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários para {@link BitcoinEventRedactor}.
 *
 * <p>Valida que a política de redação é aplicada corretamente:
 * campos permitidos passam, campos proibidos são removidos, e o envelope
 * contém apenas as chaves autorizadas.</p>
 */
class BitcoinEventRedactorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private BitcoinEventRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new BitcoinEventRedactor(mapper);
    }

    // -------------------------------------------------------------------------
    // Envelope
    // -------------------------------------------------------------------------

    @Test
    void envelopeContemCamposObrigatorios() throws Exception {
        Map<String, Object> payload = buildTransactionObservedPayload();
        String json = redactor.redact("BITCOIN_TRANSACTION_OBSERVED", payload, "corr-1", null);

        JsonNode envelope = mapper.readTree(json);

        assertNotNull(envelope.get("eventId"),       "eventId deve estar presente");
        assertEquals("BITCOIN_TRANSACTION_OBSERVED", envelope.get("eventType").asText());
        assertEquals(1, envelope.get("schemaVersion").asInt());
        assertNotNull(envelope.get("occurredAt"),    "occurredAt deve estar presente");
        assertEquals("corr-1", envelope.get("correlationId").asText());
        assertTrue(envelope.get("causationId").isNull(), "causationId deve ser null");
        assertNotNull(envelope.get("payload"),       "payload deve estar presente");
    }

    // -------------------------------------------------------------------------
    // BITCOIN_TRANSACTION_OBSERVED
    // -------------------------------------------------------------------------

    @Test
    void transactionObservedMantemCamposPublicos() throws Exception {
        Map<String, Object> payload = buildTransactionObservedPayload();
        String json = redactor.redact("BITCOIN_TRANSACTION_OBSERVED", payload, null, null);

        JsonNode p = mapper.readTree(json).get("payload");

        assertEquals("mainnet",             p.get("network").asText());
        assertEquals("bc1qtest",            p.get("address").asText());
        assertEquals("abcdef1234",          p.get("txid").asText());
        assertEquals("PENDING",             p.get("status").asText());
        assertEquals(100_000L,              p.get("totalReceivedSats").asLong());
    }

    @Test
    void transactionObservedMantemOutputsComCamposPublicos() throws Exception {
        Map<String, Object> payload = buildTransactionObservedPayload();
        String json = redactor.redact("BITCOIN_TRANSACTION_OBSERVED", payload, null, null);

        JsonNode outputs = mapper.readTree(json).get("payload").get("outputs");
        assertNotNull(outputs, "outputs deve estar presente");
        assertTrue(outputs.isArray());
        assertEquals(1, outputs.size());

        JsonNode out = outputs.get(0);
        assertEquals(0,          out.get("vout").asInt());
        assertEquals("bc1qtest", out.get("address").asText());
        assertEquals(100_000L,   out.get("amountSats").asLong());
    }

    @Test
    void transactionObservedRemoveCampoPrivado() throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(buildTransactionObservedPayload());
        payload.put("accountId", "priv-account-123");
        payload.put("petId",     "priv-pet-456");
        payload.put("email",     "user@example.com");

        String json = redactor.redact("BITCOIN_TRANSACTION_OBSERVED", payload, null, null);
        JsonNode p = mapper.readTree(json).get("payload");

        assertFalse(p.has("accountId"), "accountId não deve aparecer no payload público");
        assertFalse(p.has("petId"),     "petId não deve aparecer no payload público");
        assertFalse(p.has("email"),     "email não deve aparecer no payload público");
    }

    // -------------------------------------------------------------------------
    // BITCOIN_TRANSACTION_CONFIRMED
    // -------------------------------------------------------------------------

    @Test
    void transactionConfirmedContemBlock() throws Exception {
        Map<String, Object> block = Map.of("hash", "blockhash123", "height", 800_000, "confirmations", 3);
        Map<String, Object> payload = Map.of(
                "network", "mainnet",
                "address", "bc1qtest",
                "txid", "abcdef1234",
                "previousStatus", "PENDING",
                "status", "CONFIRMED",
                "source", "INDEXER",
                "outputs", List.of(Map.of("vout", 0, "address", "bc1qtest", "amountSats", 100_000L)),
                "totalReceivedSats", 100_000L,
                "block", block
        );

        String json = redactor.redact("BITCOIN_TRANSACTION_CONFIRMED", payload, null, null);
        JsonNode p = mapper.readTree(json).get("payload");

        assertNotNull(p.get("block"), "block deve estar presente");
        assertEquals("blockhash123", p.get("block").get("hash").asText());
        assertEquals(800_000,        p.get("block").get("height").asInt());
        assertEquals("PENDING",      p.get("previousStatus").asText());
        assertEquals("CONFIRMED",    p.get("status").asText());
    }

    // -------------------------------------------------------------------------
    // BITCOIN_TRANSACTION_REPLACED
    // -------------------------------------------------------------------------

    @Test
    void transactionReplacedContemCamposEspecificos() throws Exception {
        Map<String, Object> payload = Map.of(
                "network", "mainnet",
                "address", "bc1qtest",
                "replacedTxid", "orig-txid",
                "replacementTxid", "repl-txid",
                "replacedStatus", "REPLACED",
                "replacementStatus", "PENDING",
                "conflictInputs", List.of(Map.of("txid", "input-txid", "vout", 0)),
                "replacedReceivedSats", 100_000L,
                "replacementReceivedSats", 80_000L
        );

        String json = redactor.redact("BITCOIN_TRANSACTION_REPLACED", payload, null, null);
        JsonNode p = mapper.readTree(json).get("payload");

        assertEquals("orig-txid",  p.get("replacedTxid").asText());
        assertEquals("repl-txid",  p.get("replacementTxid").asText());
        assertEquals(100_000L,     p.get("replacedReceivedSats").asLong());
        assertEquals(80_000L,      p.get("replacementReceivedSats").asLong());
    }

    @Test
    void transactionReplacedNaoExpoeCamposPrivadosDeInputs() throws Exception {
        // conflictInputs[] permite apenas "txid" e "vout" — não outros campos
        Map<String, Object> conflictInput = new java.util.LinkedHashMap<>();
        conflictInput.put("txid", "input-txid");
        conflictInput.put("vout", 0);
        conflictInput.put("secret", "should-be-stripped");

        Map<String, Object> payload = Map.of(
                "network", "mainnet",
                "address", "bc1qtest",
                "replacedTxid", "orig-txid",
                "replacementTxid", "repl-txid",
                "replacedStatus", "REPLACED",
                "replacementStatus", "PENDING",
                "conflictInputs", List.of(conflictInput),
                "replacedReceivedSats", 100_000L,
                "replacementReceivedSats", 80_000L
        );

        String json = redactor.redact("BITCOIN_TRANSACTION_REPLACED", payload, null, null);
        JsonNode conflicts = mapper.readTree(json).get("payload").get("conflictInputs");

        assertNotNull(conflicts);
        assertTrue(conflicts.isArray());
        assertEquals(1, conflicts.size());
        assertFalse(conflicts.get(0).has("secret"), "campo 'secret' não deve aparecer em conflictInputs");
    }

    // -------------------------------------------------------------------------
    // BITCOIN_TRANSACTION_DROPPED
    // -------------------------------------------------------------------------

    @Test
    void transactionDroppedContemEvidence() throws Exception {
        Map<String, Object> evidence = Map.of(
                "kind", "MEMPOOL_EVICTED",
                "reconciledAt", "2026-01-01T12:00:00Z",
                "tip", Map.of("hash", "tiphash", "height", 800_001)
        );
        Map<String, Object> payload = Map.of(
                "network", "mainnet",
                "address", "bc1qtest",
                "txid", "dropped-txid",
                "previousStatus", "PENDING",
                "status", "DROPPED",
                "reason", "MEMPOOL_EVICTED",
                "evidence", evidence,
                "invalidatedReceivedSats", 100_000L
        );

        String json = redactor.redact("BITCOIN_TRANSACTION_DROPPED", payload, null, null);
        JsonNode p = mapper.readTree(json).get("payload");

        assertNotNull(p.get("evidence"), "evidence deve estar presente");
        assertEquals("MEMPOOL_EVICTED", p.get("evidence").get("kind").asText());
        assertEquals("tiphash", p.get("evidence").get("tip").get("hash").asText());
        assertEquals(100_000L, p.get("invalidatedReceivedSats").asLong());
    }

    // -------------------------------------------------------------------------
    // BITCOIN_BALANCE_RECONCILED
    // -------------------------------------------------------------------------

    @Test
    void balanceReconciledContemCamposDeBalance() throws Exception {
        Map<String, Object> payload = Map.of(
                "network", "mainnet",
                "address", "bc1qtest",
                "balanceStatus", "CONFIRMED",
                "confirmedBalanceSats", 500_000L,
                "pendingIncomingSats", 50_000L,
                "pendingOutgoingSats", 0L,
                "transactionsReconciled", 5,
                "reconciliationTip", Map.of("hash", "tiphash", "height", 800_000)
        );

        String json = redactor.redact("BITCOIN_BALANCE_RECONCILED", payload, null, null);
        JsonNode p = mapper.readTree(json).get("payload");

        assertEquals("CONFIRMED", p.get("balanceStatus").asText());
        assertEquals(500_000L, p.get("confirmedBalanceSats").asLong());
        assertEquals(50_000L, p.get("pendingIncomingSats").asLong());
        assertEquals("tiphash", p.get("reconciliationTip").get("hash").asText());
    }

    // -------------------------------------------------------------------------
    // Tipo de evento inválido
    // -------------------------------------------------------------------------

    @Test
    void tipoDeEventoInvalidoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () ->
                redactor.redact("EVENTO_DESCONHECIDO", Map.of("foo", "bar"), null, null)
        );
    }

    // -------------------------------------------------------------------------
    // Tipos suportados
    // -------------------------------------------------------------------------

    @Test
    void supportedEventTypesContemTodosOsTiposBitcoin() {
        var types = redactor.supportedEventTypes();

        assertTrue(types.contains("BITCOIN_TRANSACTION_OBSERVED"));
        assertTrue(types.contains("BITCOIN_TRANSACTION_CONFIRMED"));
        assertTrue(types.contains("BITCOIN_TRANSACTION_REPLACED"));
        assertTrue(types.contains("BITCOIN_TRANSACTION_DROPPED"));
        assertTrue(types.contains("BITCOIN_CHAIN_REORG"));
        assertTrue(types.contains("BITCOIN_BALANCE_RECONCILED"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildTransactionObservedPayload() {
        return Map.of(
                "network", "mainnet",
                "address", "bc1qtest",
                "txid", "abcdef1234",
                "status", "PENDING",
                "source", "INDEXER",
                "outputs", List.of(Map.of("vout", 0, "address", "bc1qtest", "amountSats", 100_000L)),
                "totalReceivedSats", 100_000L,
                "block", Map.of()
        );
    }
}
