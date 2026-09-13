package br.com.satoshipet.api.mail;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Implementação de fallback do {@link MailPort} que apenas registra em log.
 *
 * <p>Bean padrão ({@link DefaultBean}): ativo quando {@link SmtpMailPort}
 * não estiver disponível no perfil atual (ex: testes unitários e integração).</p>
 *
 * <p>Nunca registra o link bruto — apenas o e-mail de destino.</p>
 */
@ApplicationScoped
@DefaultBean
public class LoggingMailPort implements MailPort {

    private static final Logger LOG = Logger.getLogger(LoggingMailPort.class);

    @Override
    public void sendMagicLink(String to, String link) {
        // Link não é registrado — apenas destino para auditoria.
        LOG.infof("[LoggingMailPort] Magic link emitido para %s (link omitido do log).", to);
    }
}
