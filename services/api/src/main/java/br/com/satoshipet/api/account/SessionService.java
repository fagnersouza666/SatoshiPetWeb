package br.com.satoshipet.api.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Gerencia o ciclo de vida das sessões autenticadas.
 *
 * <p>Tokens brutos são gerados com {@link SecureRandom} e jamais persistidos —
 * apenas o hash SHA-256 (via {@link SessionTokenHasher}) fica no banco.</p>
 */
@ApplicationScoped
public class SessionService {

    private static final Logger LOG = Logger.getLogger(SessionService.class);

    /** Comprimento em bytes do token de sessão (256 bits). */
    private static final int TOKEN_BYTES = 32;

    private final SessionTokenHasher hasher;
    private final SessionConfiguration configuration;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionTokenHasher hasher, SessionConfiguration configuration) {
        this.hasher = hasher;
        this.configuration = configuration;
    }

    /**
     * Cria uma nova sessão para a conta informada.
     *
     * @param account   conta autenticada
     * @param now       instante de criação
     * @param userAgent agente do cliente (para auditoria)
     * @param ip        IP de origem
     * @return resultado da criação com tokens brutos para retornar ao cliente
     */
    @Transactional
    public SessionCreation create(Account account, Instant now, String userAgent, String ip) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(now, "now");

        String rawSessionToken = generateToken();
        String rawCsrfToken    = generateToken();

        String sessionHash = hasher.hash(rawSessionToken);
        String csrfHash    = hasher.hash(rawCsrfToken);

        Session session = Session.create(
                account, sessionHash, csrfHash,
                now, now.plus(configuration.duration()),
                userAgent, ip
        );
        session.persist();

        LOG.infof("Sessão criada para account=%s id=%s", account.id, session.id);
        return new SessionCreation(rawSessionToken, rawCsrfToken, session);
    }

    /**
     * Localiza uma sessão ativa pelo token bruto.
     *
     * @param rawToken token bruto (lido do cookie sp_session)
     * @param now      instante de verificação
     * @return sessão ativa, ou vazio se inválida/expirada
     */
    public Optional<Session> findActive(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hasher.hash(rawToken);
        return Session.findActiveByTokenHash(tokenHash, now);
    }

    /**
     * Invalida a sessão identificada pelo token bruto (logout).
     *
     * @param rawToken token bruto do cookie
     * @param now      instante de invalidação
     */
    @Transactional
    public void revoke(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hasher.hash(rawToken);
        Session.findByTokenHash(tokenHash).ifPresent(s -> s.invalidate(now));
    }

    /**
     * Invalida todas as sessões ativas de uma conta (logout geral).
     *
     * @param accountId conta alvo
     * @param now       instante de invalidação
     * @return número de sessões invalidadas
     */
    @Transactional
    public long revokeAll(java.util.UUID accountId, Instant now) {
        long count = Session.invalidateAllForAccount(accountId, now);
        LOG.infof("Sessões invalidadas para account=%s count=%d", accountId, count);
        return count;
    }

    /** Gera um token aleatório seguro codificado em Base64Url sem padding. */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Resultado da criação de uma sessão.
     *
     * @param rawSessionToken token bruto de sessão (retornar no cookie sp_session)
     * @param rawCsrfToken    token CSRF bruto (retornar no header X-CSRF-Token)
     * @param session         entidade da sessão criada
     */
    public record SessionCreation(
            String rawSessionToken,
            String rawCsrfToken,
            Session session
    ) {}
}
