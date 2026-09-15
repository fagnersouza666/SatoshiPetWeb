package br.com.satoshipet.api.art;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PetArtworkReadyEventSchemaTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void payloadReadyContemSomenteCamposPublicos() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("address", "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        payload.put("eventType", ArtworkPipeline.PET_ARTWORK_READY);
        payload.put("occurredAt", "2026-09-14T15:00:00Z");
        payload.put("presentation", "CREATURE");
        payload.put("artworkVersion", 1);
        payload.put("atlasUrl", "/api/v1/public/addresses/bc1q/artwork/1/atlas.png");

        JsonNode schema = objectMapper.readTree(Files.readString(schemaPath()));
        JsonNode node = objectMapper.valueToTree(payload);

        for (String required : requiredFields(schema)) {
            assertTrue(node.has(required), "Campo obrigatório ausente: " + required);
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            assertTrue(schema.path("properties").has(field), "Campo proibido: " + field);
        }
        assertFalse(node.has("prompt_private"));
        assertFalse(node.has("petId"));
    }

    private static Path schemaPath() {
        return Path.of("..", "..", "docs", "contratos", "schemas", "pet-events", "v1", "artwork-ready.schema.json")
                .normalize();
    }

    private static Set<String> requiredFields(JsonNode schema) {
        JsonNode required = schema.path("required");
        java.util.HashSet<String> fields = new java.util.HashSet<>();
        if (required.isArray()) {
            required.forEach(n -> fields.add(n.asText()));
        }
        return fields;
    }
}
