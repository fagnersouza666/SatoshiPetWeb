package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.platform.CorrelationIdContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redação de eventos Bitcoin segundo a política de projeção pública.
 *
 * <p>Lê a política em {@code redaction-policy.json} do classpath e aplica a
 * allowlist de campos para cada tipo de evento (PRD §16.3, CA-009).</p>
 *
 * <p>Garante que nenhum campo da denylist (accountId, petId, email, token, etc.)
 * apareça no payload de saída.</p>
 */
@ApplicationScoped
public class BitcoinEventRedactor {

    private static final Logger LOG = Logger.getLogger(BitcoinEventRedactor.class);

    /** Versão do schema do envelope de eventos Bitcoin. */
    private static final int SCHEMA_VERSION = 1;

    /** Tipo do agregado para eventos Bitcoin (aggregate_type no outbox). */
    public static final String AGGREGATE_TYPE = "Address";

    private final ObjectMapper mapper;

    /** Cache da política carregada do classpath. */
    private volatile JsonNode policy;

    @Inject
    public BitcoinEventRedactor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Constrói e redige um envelope de evento Bitcoin com payload filtrado.
     *
     * @param eventType     tipo do evento (ex: "BITCOIN_TRANSACTION_OBSERVED")
     * @param payload       mapa de campos do payload bruto (nunca enviado sem redação)
     * @param correlationId identificador de correlação (opcional; o contexto
     *                      atual ou um novo UUID é usado quando ausente)
     * @param causationId   identificador de causação (pode ser nulo)
     * @return JSON string do envelope redagido, pronto para persistir no outbox
     * @throws IllegalArgumentException se o eventType não estiver na política
     */
    public String redact(String eventType, Map<String, Object> payload,
                         String correlationId, String causationId) {
        JsonNode policyNode = loadPolicy();
        JsonNode allowedPaths = policyNode.path("allowedPayloadPaths").get(eventType);

        if (allowedPaths == null || allowedPaths.isNull()) {
            throw new IllegalArgumentException("Tipo de evento não definido na política: " + eventType);
        }

        List<String> paths = new ArrayList<>();
        allowedPaths.forEach(node -> paths.add(node.asText()));

        // Converte payload para JsonNode e filtra
        JsonNode rawPayload = mapper.valueToTree(payload);
        JsonNode redactedPayload = filterNode(rawPayload, paths);

        // Monta o envelope
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("correlationId", CorrelationIdContext.resolve(correlationId));
        if (causationId != null) envelope.put("causationId", causationId);
        else envelope.putNull("causationId");
        envelope.set("payload", redactedPayload);

        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao serializar envelope do evento %s", eventType);
            throw new RuntimeException("Falha ao serializar evento Bitcoin", e);
        }
    }

    /**
     * Retorna o conjunto de tipos de evento definidos na política.
     */
    public Set<String> supportedEventTypes() {
        JsonNode policyNode = loadPolicy();
        Set<String> types = new java.util.LinkedHashSet<>();
        policyNode.path("allowedPayloadPaths").fieldNames().forEachRemaining(types::add);
        return Set.copyOf(types);
    }

    // -------------------------------------------------------------------------
    // Carregamento da política
    // -------------------------------------------------------------------------

    private JsonNode loadPolicy() {
        if (policy != null) return policy;
        synchronized (this) {
            if (policy != null) return policy;
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("redaction-policy.json")) {
                if (is == null) {
                    throw new IllegalStateException(
                            "redaction-policy.json não encontrado no classpath");
                }
                policy = mapper.readTree(is);
                LOG.infof("Política de redação Bitcoin carregada (v%d, projection=%s)",
                        policy.get("policyVersion").asInt(),
                        policy.get("projection").asText());
                return policy;
            } catch (IOException e) {
                throw new RuntimeException("Falha ao carregar redaction-policy.json", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Filtro de payload por allowlist de caminhos
    // -------------------------------------------------------------------------

    /**
     * Filtra um JsonNode mantendo apenas os caminhos da lista de permitidos.
     *
     * @param node         nó a filtrar
     * @param allowedPaths caminhos relativos permitidos (ex: "network", "block.hash", "outputs[].vout")
     * @return nó filtrado
     */
    JsonNode filterNode(JsonNode node, List<String> allowedPaths) {
        if (node == null || node.isNull() || node.isMissingNode()) return node;

        if (node.isObject()) {
            return filterObject((ObjectNode) node, allowedPaths);
        } else if (node.isArray()) {
            return filterArray((ArrayNode) node, allowedPaths);
        }
        return node;
    }

    private JsonNode filterObject(ObjectNode node, List<String> allowedPaths) {
        // Agrupa paths pelo primeiro segmento
        Map<String, List<String>> grouped = groupByFirstSegment(allowedPaths);

        ObjectNode result = mapper.createObjectNode();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            String field = entry.getKey();
            List<String> remainders = entry.getValue();

            if (!node.has(field)) continue;

            JsonNode child = node.get(field);
            List<String> childPaths = remainders.stream()
                    .filter(r -> !r.isEmpty())
                    .toList();

            if (childPaths.isEmpty()) {
                // Caminho folha — inclui o valor inteiro
                result.set(field, child);
            } else {
                // Tem filhos — filtra recursivamente
                result.set(field, filterNode(child, childPaths));
            }
        }
        return result;
    }

    private JsonNode filterArray(ArrayNode node, List<String> allowedPaths) {
        // Caminhos que chegam aqui começam com "[]" ou "[]."
        List<String> elementPaths = allowedPaths.stream()
                .filter(p -> p.startsWith("[].") || p.equals("[]"))
                .map(p -> p.equals("[]") ? "" : p.substring(3))
                .toList();

        ArrayNode result = mapper.createArrayNode();
        for (JsonNode element : node) {
            if (elementPaths.contains("") || elementPaths.isEmpty()) {
                result.add(element);
            } else {
                result.add(filterNode(element, elementPaths));
            }
        }
        return result;
    }

    /**
     * Agrupa caminhos pelo primeiro segmento.
     *
     * <p>Exemplos:
     * <ul>
     *   <li>"network" → {"network": [""]}</li>
     *   <li>"block.hash" → {"block": ["hash"]}</li>
     *   <li>"outputs[].vout" → {"outputs": ["[].vout"]}</li>
     * </ul></p>
     */
    private Map<String, List<String>> groupByFirstSegment(List<String> paths) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();

        for (String path : paths) {
            int dotIdx = path.indexOf('.');
            int arrIdx = path.indexOf("[]");

            String firstSegment;
            String remainder;

            if (dotIdx == -1 && arrIdx == -1) {
                // Caminho folha simples: "network"
                firstSegment = path;
                remainder = "";
            } else if (arrIdx != -1 && (dotIdx == -1 || arrIdx <= dotIdx)) {
                // Caminho de array: "outputs[].vout" ou "outputs[]"
                firstSegment = arrIdx == 0 ? "" : path.substring(0, arrIdx);
                if (firstSegment.isEmpty()) {
                    // Estamos dentro do array; remainder é o restante
                    firstSegment = "[]";
                    remainder = dotIdx == -1 ? "" : path.substring(dotIdx + 1);
                } else {
                    remainder = path.substring(arrIdx); // "[].vout"
                }
            } else {
                // Caminho aninhado: "block.hash"
                firstSegment = path.substring(0, dotIdx);
                remainder = path.substring(dotIdx + 1);
            }

            grouped.computeIfAbsent(firstSegment, k -> new ArrayList<>()).add(remainder);
        }

        return grouped;
    }
}
