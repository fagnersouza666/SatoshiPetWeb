package br.com.satoshipet.api.mail;

/**
 * Porta de saída para envio de e-mails transacionais.
 *
 * <p>Adapters disponíveis:
 * <ul>
 *   <li>{@link SmtpMailPort}    — SMTP real via Quarkus Mailer (dev: Mailpit)</li>
 *   <li>{@link LoggingMailPort} — registra em log sem enviar (testes)</li>
 * </ul>
 * </p>
 */
public interface MailPort {

    /**
     * Envia o magic link de autenticação para o e-mail informado.
     *
     * <p>O link bruto nunca é registrado em log. Somente o e-mail de destino
     * pode aparecer em logs de auditoria.</p>
     *
     * @param to   endereço de destino (sanitizado antes de logar)
     * @param link URL completa do magic link (não logar)
     */
    void sendMagicLink(String to, String link);
}
