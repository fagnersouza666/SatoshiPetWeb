package br.com.satoshipet.api.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitcoinEventRedactionContractTest {

    private static final Set<String> EVENT_TYPES = Set.of(
            "BITCOIN_TRANSACTION_OBSERVED",
            "BITCOIN_TRANSACTION_CONFIRMED",
            "BITCOIN_TRANSACTION_REPLACED",
            "BITCOIN_TRANSACTION_DROPPED",
            "BITCOIN_CHAIN_REORG",
            "BITCOIN_BALANCE_RECONCILED"
    );

    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId",
            "eventType",
            "schemaVersion",
            "occurredAt",
            "correlationId",
            "causationId",
            "payload"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void politicaDefineAllowlistPublicaParaTodosOsEventosBitcoin() throws IOException {
        JsonNode policy = readPolicy();

        assertEquals(1, policy.get("policyVersion").asInt());
        assertEquals("public", policy.get("projection").asText());
        assertEquals(ENVELOPE_FIELDS, fields(policy.get("allowedEnvelopeFields")));
        assertEquals(EVENT_TYPES, fields(policy.get("allowedPayloadPaths")));

        JsonNode serialization = policy.get("serialization");
        assertEquals("forbidden", serialization.get("entitySerialization").asText());
        assertEquals("omit-before-serialization", serialization.get("privateFieldAction").asText());
        assertEquals("reject-unknown-properties", serialization.get("validationAction").asText());
        assertEquals("reject-event", serialization.get("missingRequiredPublicFieldAction").asText());
    }

    @Test
    void allowlistNaoContemCamposDaDenylist() throws IOException {
        JsonNode policy = readPolicy();
        Set<String> forbidden = fields(policy.get("forbiddenFieldNames"));

        assertTrue(forbidden.containsAll(Set.of(
                "accountId",
                "petId",
                "bindingId",
                "logicalReceiptId",
                "email",
                "prompt",
                "recoveryCode",
                "token",
                "secret",
                "xprv"
        )));

        Iterator<JsonNode> payloadPaths = policy.get("allowedPayloadPaths").elements();
        while (payloadPaths.hasNext()) {
            JsonNode paths = payloadPaths.next();
            for (JsonNode path : paths) {
                for (String segment : path.asText().split("\\.")) {
                    assertFalse(forbidden.contains(segment.replace("[]", "")),
                            () -> "Campo privado na allowlist: " + path.asText());
                }
            }
        }
    }

    @Test
    void politicaExigeFalhaFechadaEProibePlaceholdersPrivados() throws IOException {
        JsonNode serialization = readPolicy().get("serialization");

        assertEquals("forbidden", serialization.get("privateValuePlaceholder").asText());
        assertTrue(serialization.get("unknownFieldAction").asText().startsWith("omit"));
    }

    private JsonNode readPolicy() throws IOException {
        return objectMapper.readTree(Files.readString(policyPath()));
    }

    private Path policyPath() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(
                    "docs/contratos/schemas/bitcoin-events/v1/redaction-policy.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Política de redaction não encontrada");
    }

    private Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        node.elements().forEachRemaining(element -> {
            if (element.isValueNode()) {
                result.add(element.asText());
            }
        });
        return result;
    }
}
