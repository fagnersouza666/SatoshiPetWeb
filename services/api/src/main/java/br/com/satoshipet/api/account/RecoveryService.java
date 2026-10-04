package br.com.satoshipet.api.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import br.com.satoshipet.api.auth.magiclink.MagicLinkRequest;

/**
 * Gerencia a geração e o uso de códigos de recuperação de conta.
 *
 * <p>O código bruto é apresentado uma única vez ao usuário e nunca armazenado.
 * Somente o hash SHA-256 é persistido em {@link RecoveryCode}.</p>
 */
@ApplicationScoped
public class RecoveryService {

    private static final Logger LOG = Logger.getLogger(RecoveryService.class);
    private static final int CODE_BYTES = 16; // 128 bits

    private final SessionTokenHasher hasher;
    private final SessionService sessionService;
    private final SecureRandom random = new SecureRandom();

    @Inject MagicLinkTokenConfiguration tokenConfiguration;
    @Inject Validator validator;

    public RecoveryService(SessionTokenHasher hasher, SessionService sessionService) {
        this.hasher = hasher;
        this.sessionService = sessionService;
    }

    /**
     * Gera um novo código de recuperação para a conta, substituindo o anterior.
     * Retorna o código bruto que deve ser apresentado ao usuário uma única vez.
     *
     * @param account conta autenticada
     * @param now     instante de geração
     * @return código bruto de recuperação (apresentar apenas uma vez)
     */
    @Transactional
    public String generate(Account account, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(now, "now");

        AccountMutationLock.acquire();
        Account current = Account.findById(account.id);
        if (current == null) throw invalidCode();

        // Invalida verificadores anteriores sem apagar o registro de consumo em andamento.
        RecoveryCode.update("usedAt = ?1 WHERE account.id = ?2 AND usedAt IS NULL", now, account.id);

        // Gera novo código
        String rawCode = generateCode();
        String codeHash = hasher.hash(rawCode);

        RecoveryCode code = RecoveryCode.create(current, codeHash, now);
        code.persist();

        LOG.infof("Código de recuperação gerado para account=%s", account.id);
        return rawCode;
    }

    /**
     * Usa um código de recuperação para invalidar todas as sessões e criar uma nova.
     *
     * @param rawCode   código bruto de recuperação
     * @param now       instante da operação
     * @param userAgent agente do cliente
     * @param ip        IP de origem
     * @return nova sessão criada
     * @throws RecoveryException se o código for inválido ou já usado
     */
    @Transactional
    public SessionService.SessionCreation recover(
            String rawCode, Instant now, String userAgent, String ip
    ) {
        throw new RecoveryException("email_verification_required", "Verifique o novo e-mail para recuperar a conta.");
    }

    /** Persiste o desafio; a chamada SMTP ocorre no resource depois deste commit. */
    @Transactional
    public EmailVerification requestEmail(String rawCode, String newEmail, Instant now) {
        Objects.requireNonNull(now, "now");
        String email = newEmail == null ? "" : newEmail.trim().toLowerCase(Locale.ROOT);
        if (!validator.validate(new MagicLinkRequest(email)).isEmpty()) {
            throw new RecoveryException("invalid_email", "Informe um e-mail válido.");
        }
        AccountMutationLock.acquire();
        RecoveryCode code = requireCode(rawCode, now);
        ensureEmailAvailable(email, code.account.id);
        String rawToken = generateVerificationToken();
        RecoveryEmailToken challenge = new RecoveryEmailToken();
        challenge.id = UUID.randomUUID();
        challenge.account = code.account;
        challenge.recoveryCode = code;
        challenge.tokenHash = hasher.hash(rawToken);
        challenge.newEmail = email;
        challenge.issuedAt = now;
        challenge.expiresAt = now.plus(tokenConfiguration.requiredTtl());
        challenge.persist();
        return new EmailVerification(email, rawToken);
    }

    @Transactional
    public RecoveryResult recover(String rawCode, String rawToken, Instant now, String userAgent, String ip) {
        Objects.requireNonNull(now, "now");
        if (rawToken == null || rawToken.isBlank()) throw invalidCode();
        AccountMutationLock.acquire();
        RecoveryCode code = requireCode(rawCode, now);
        RecoveryEmailToken challenge = RecoveryEmailToken.find("tokenHash", hasher.hash(rawToken)).firstResult();
        if (challenge == null || !challenge.usableAt(now)
                || !challenge.account.id.equals(code.account.id)
                || !challenge.recoveryCode.id.equals(code.id)) throw invalidCode();
        Account account = code.account;
        ensureEmailAvailable(challenge.newEmail, account.id);
        if (RecoveryCode.consumeIfAvailable(code.id, now) != 1) throw invalidCode();
        if (RecoveryEmailToken.update("consumedAt = ?1 WHERE id = ?2 AND consumedAt IS NULL AND expiresAt > ?1",
                now, challenge.id) != 1) throw invalidCode();
        code.markUsed(now);
        challenge.consumedAt = now;
        Account.getEntityManager().createQuery("DELETE FROM MagicLinkToken WHERE email = :email")
                .setParameter("email", account.email).executeUpdate();
        account.email = challenge.newEmail;
        sessionService.revokeAll(account.id, now);
        String nextCode = generate(account, now);
        SessionService.SessionCreation session = sessionService.create(account, now, userAgent, ip);
        return new RecoveryResult(session, nextCode);
    }

    private RecoveryCode requireCode(String rawCode, Instant now) {
        if (rawCode == null || rawCode.isBlank()) throw invalidCode();
        RecoveryCode code = RecoveryCode.findByCodeHash(hasher.hash(rawCode)).orElseThrow(RecoveryService::invalidCode);
        if (!code.isUsable() || now.isBefore(code.createdAt)) throw invalidCode();
        return code;
    }

    private void ensureEmailAvailable(String email, UUID accountId) {
        Account existing = Account.find("lower(email)", email).firstResult();
        if (existing != null && !existing.id.equals(accountId)) {
            throw new RecoveryException("email_unavailable", "O e-mail informado não está disponível para recuperação.");
        }
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static RecoveryException invalidCode() {
        return new RecoveryException("invalid_code", "Código ou verificação inválidos, expirados ou já usados.");
    }

    public record EmailVerification(String email, String token) {}
    public record RecoveryResult(SessionService.SessionCreation session, String recoveryCode) {}

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Exceção de domínio para falhas de recuperação. */
    public static class RecoveryException extends RuntimeException {
        private final String code;

        public RecoveryException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
