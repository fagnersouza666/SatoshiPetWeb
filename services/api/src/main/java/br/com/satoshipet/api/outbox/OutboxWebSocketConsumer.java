package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.realtime.AccountWebSocket;
import br.com.satoshipet.api.realtime.AddressWebSocket;
import br.com.satoshipet.api.realtime.RealtimeEventCursorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Objects;

/**
 * Consumidor do outbox transacional que transmite eventos de endereço
 * para todos os clientes WebSocket conectados ao canal correspondente.
 *
 * <p>Suporta eventos cujo tipo começa com {@code BITCOIN_}, {@code PET_}
 * ou {@code TEST_} (prefixo {@code TEST_} reservado para testes automatizados).</p>
 *
 * <p>O {@code aggregate_id} é tratado como o endereço canônico quando
 * {@code PET_*} segue só no canal público de endereço; a fila autenticada
 * permanece em {@code GET /api/v1/account/pet/presentation-queue} até o
 * WebSocket de conta exigir sessão.
 */
@ApplicationScoped
public class OutboxWebSocketConsumer implements OutboxConsumer {

    private static final Logger LOG = Logger.getLogger(OutboxWebSocketConsumer.class);

    private final AddressWebSocket addressWebSocket;
    private final AccountWebSocket accountWebSocket;
    private final RealtimeEventCursorService cursorService;
    private final ObjectMapper objectMapper;

    public OutboxWebSocketConsumer(
            AddressWebSocket addressWebSocket,
            AccountWebSocket accountWebSocket,
            RealtimeEventCursorService cursorService,
            ObjectMapper objectMapper
    ) {
        this.addressWebSocket = addressWebSocket;
        this.accountWebSocket = Objects.requireNonNull(accountWebSocket, "accountWebSocket");
        this.cursorService = cursorService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        if (eventType == null) {
            return false;
        }
        return eventType.startsWith("BITCOIN_")
                || eventType.startsWith("PET_")
                || eventType.startsWith("TEST_");
    }

    /**
     * Processa o evento: determina o endereço canônico, avança o cursor e
     * transmite o evento via WebSocket.
     *
     * <p>A implementação é idempotente: o publisher pode invocar novamente em
     * caso de falha transitória — o cursor avançará novamente mas o cliente
     * descartará duplicatas pelo cursor.</p>
     */
    @Override
    public void consume(OutboxEvent event) {
        String canonical = resolveCanonical(event);
        if (canonical == null) {
            LOG.warnf("Não foi possível determinar endereço canônico para event id=%s type=%s",
                    event.id, event.eventType);
            return;
        }

        Object payload = parsePayload(event);
        String cursor = cursorService.nextCursor(canonical, event.eventType, payload);

        LOG.debugf("Transmitindo event id=%s type=%s address=%s cursor=%s",
                event.id, event.eventType, canonical, cursor);

        addressWebSocket.broadcast(canonical, cursor, payload);
    }

    /**
     * Resolve o endereço canônico: quando {@code aggregate_type} for "Address",
     * usa {@code aggregate_id} diretamente; caso contrário tenta extrair o campo
     * {@code address} do payload JSON.
     */
    String resolveCanonical(OutboxEvent event) {
        if ("Address".equals(event.aggregateType)) {
            return event.aggregateId;
        }
        // Tenta campo "address" no payload como fallback
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    event.payload,
                    new TypeReference<Map<String, Object>>() {}
            );
            Object address = payload.get("address");
            return address instanceof String s ? s : null;
        } catch (Exception e) {
            LOG.debugf("Payload não é JSON com campo address: %s", event.payload);
            return null;
        }
    }

    /** Deserializa o payload JSON em Map genérico para transmissão via WebSocket. */
    private Object parsePayload(OutboxEvent event) {
        try {
            return objectMapper.readValue(
                    event.payload,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            LOG.warnf("Falha ao parsear payload do evento id=%s — transmitindo string bruta", event.id);
            return event.payload;
        }
    }
}
