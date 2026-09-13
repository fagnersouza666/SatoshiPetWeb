package br.com.satoshipet.api.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

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

        // Remove códigos anteriores
        RecoveryCode.deleteByAccount(account);

        // Gera novo código
        String rawCode = generateCode();
        String codeHash = hasher.hash(rawCode);

        RecoveryCode code = RecoveryCode.create(account, codeHash, now);
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
        Objects.requireNonNull(rawCode, "rawCode");
        Objects.requireNonNull(now, "now");

        String codeHash = hasher.hash(rawCode);
        Optional<RecoveryCode> candidate = RecoveryCode.findByCodeHash(codeHash);

        if (candidate.isEmpty() || !candidate.get().isUsable()) {
            throw new RecoveryException("invalid_code", "Código de recuperação inválido ou já usado.");
        }

        RecoveryCode recoveryCode = candidate.get();
        recoveryCode.markUsed(now);

        Account account = recoveryCode.account;

        // Invalida todas as sessões existentes
        sessionService.revokeAll(account.id, now);

        // Cria nova sessão
        SessionService.SessionCreation creation = sessionService.create(account, now, userAgent, ip);

        LOG.infof("Conta recuperada via código: account=%s", account.id);
        return creation;
    }

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
