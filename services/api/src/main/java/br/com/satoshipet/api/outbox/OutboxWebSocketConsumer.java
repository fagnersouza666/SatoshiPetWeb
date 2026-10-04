package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.realtime.RealtimeEventCursorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Map;

/** Persiste a projeção pública; cada réplica distribui os eventos após commit. */
@ApplicationScoped
public class OutboxWebSocketConsumer implements OutboxConsumer {
    private static final Logger LOG = Logger.getLogger(OutboxWebSocketConsumer.class);
    private final RealtimeEventCursorService cursorService;
    private final ObjectMapper objectMapper;

    @Inject
    public OutboxWebSocketConsumer(RealtimeEventCursorService cursorService, ObjectMapper objectMapper) {
        this.cursorService = cursorService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && (eventType.startsWith("BITCOIN_")
                || eventType.startsWith("PET_") || eventType.startsWith("TEST_"));
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void consume(OutboxEvent event) {
        String canonical = resolveCanonical(event);
        if (canonical == null || canonical.isBlank()) {
            LOG.warnf("Evento público sem endereço: id=%s type=%s", event.id, event.eventType);
            return;
        }
        cursorService.record(event, canonical);
    }

    String resolveCanonical(OutboxEvent event) {
        if ("Address".equals(event.aggregateType)) return event.aggregateId;
        try {
            Map<String, Object> payload = objectMapper.readValue(event.payload, new TypeReference<>() {});
            Object address = payload.get("address");
            return address instanceof String value ? value : null;
        } catch (Exception exception) {
            // O conteúdo bruto nunca é registrado em logs operacionais.
            return null;
        }
    }
}
