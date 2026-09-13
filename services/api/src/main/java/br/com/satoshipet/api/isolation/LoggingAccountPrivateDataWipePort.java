package br.com.satoshipet.api.isolation;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Implementação de fallback do {@link AccountPrivateDataWipePort} que apenas registra em log.
 *
 * <p>Bean padrão ({@link DefaultBean}): ativo quando a implementação real
 * do épico CONTA não estiver disponível. Nunca apaga dados reais.</p>
 */
@ApplicationScoped
@DefaultBean
public class LoggingAccountPrivateDataWipePort implements AccountPrivateDataWipePort {

    private static final Logger LOG = Logger.getLogger(LoggingAccountPrivateDataWipePort.class);

    @Override
    public void wipe(UUID accountId) {
        // Implementação real virá com a conclusão do épico CONTA (LGPD CA-070).
        LOG.infof("[LoggingAccountPrivateDataWipePort] Wipe solicitado para account=%s (stub — sem ação real).", accountId);
    }
}
