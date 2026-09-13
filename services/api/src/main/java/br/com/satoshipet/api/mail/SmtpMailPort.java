package br.com.satoshipet.api.mail;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Envia e-mails via SMTP usando o Quarkus Mailer.
 * Em dev, o Mailer aponta para o Mailpit; em prod, para o servidor SMTP configurado.
 * Ativo apenas nos perfis {@code dev} e {@code prod}.
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "prod"})
public class SmtpMailPort implements MailPort {

    private static final Logger LOG = Logger.getLogger(SmtpMailPort.class);

    /** Assunto padrão do e-mail de magic link. */
    private static final String MAGIC_LINK_SUBJECT = "Seu link de acesso ao Satoshi Pet";

    @Inject
    Mailer mailer;

    @Override
    public void sendMagicLink(String to, String link) {
        // O link bruto não é registrado em log (requisito de segurança).
        LOG.infof("Enviando magic link para %s", maskEmail(to));

        String body = """
                Olá!

                Clique no link abaixo para acessar sua conta no Satoshi Pet.
                O link é válido por 15 minutos e pode ser usado apenas uma vez.

                %s

                Se você não solicitou este acesso, ignore este e-mail.
                """.formatted(link);

        mailer.send(
                Mail.withText(to, MAGIC_LINK_SUBJECT, body)
        );
    }

    /** Mascara o e-mail para log: "pess***@example.com". */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return "***";
        return email.substring(0, 3) + "***" + email.substring(at);
    }
}
