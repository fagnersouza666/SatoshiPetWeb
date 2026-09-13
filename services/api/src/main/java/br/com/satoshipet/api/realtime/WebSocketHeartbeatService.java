package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Heartbeat periódico para todos os canais WebSocket.
 *
 * <p>Envia {@code PING} a cada 30 s para manter conexões ativas e detectar
 * clientes desconectados. Separado dos endpoints para evitar mistura de
 * anotações WebSocket com Scheduler no mesmo bean.</p>
 */
@ApplicationScoped
public class WebSocketHeartbeatService {

    private static final Logger LOG = Logger.getLogger(WebSocketHeartbeatService.class);

    @Inject
    OpenConnections openConnections;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Envia PING para todas as conexões abertas a cada 30 s.
     */
    @Scheduled(every = "30s", identity = "ws-heartbeat")
    public void heartbeat() {
        String ping;
        try {
            ping = objectMapper.writeValueAsString(new WebSocketPing());
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Falha ao serializar PING");
            return;
        }

        openConnections.stream()
                .filter(c -> c.isOpen())
                .forEach(c -> {
                    try {
                        c.sendTextAndAwait(ping);
                    } catch (Exception e) {
                        LOG.debugf("Falha ao enviar PING para conexão %s", c.id());
                    }
                });
    }
}
