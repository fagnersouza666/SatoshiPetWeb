package br.com.satoshipet.api.isolation;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Implementação de fallback do {@link PushSubscriptionPort} que apenas registra em log.
 *
 * <p>Bean padrão ({@link DefaultBean}): ativo enquanto o épico PUSH não estiver
 * implementado. Nunca envia notificações reais.</p>
 */
@ApplicationScoped
@DefaultBean
public class LoggingPushSubscriptionPort implements PushSubscriptionPort {

    private static final Logger LOG = Logger.getLogger(LoggingPushSubscriptionPort.class);

    @Override
    public void subscribe(UUID accountId, String endpoint, String p256dhKey, String authSecret) {
        LOG.infof("[LoggingPushSubscriptionPort] subscribe account=%s endpoint=%s (stub).", accountId, endpoint);
    }

    @Override
    public void unsubscribe(UUID accountId, String endpoint) {
        LOG.infof("[LoggingPushSubscriptionPort] unsubscribe account=%s endpoint=%s (stub).", accountId, endpoint);
    }

    @Override
    public void send(UUID accountId, String payload) {
        LOG.infof("[LoggingPushSubscriptionPort] send account=%s payload-length=%d (stub).",
                accountId, payload != null ? payload.length() : 0);
    }
}
