package br.com.satoshipet.api.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Emissão e consumo persistentes do verificador de magic link. */
@ApplicationScoped
public class MagicLinkTokenService {

    private final MagicLinkTokenRepository repository;
    private final MagicLinkTokenConfiguration configuration;
    private final MagicLinkTokenHasher hasher;

    public MagicLinkTokenService(
            MagicLinkTokenRepository repository,
            MagicLinkTokenConfiguration configuration,
            MagicLinkTokenHasher hasher
    ) {
        this.repository = repository;
        this.configuration = configuration;
        this.hasher = hasher;
    }

    /** Persiste somente o hash e o prazo; o token bruto permanece com o emissor. */
    @Transactional
    public MagicLinkToken issue(String email, String rawToken, Instant issuedAt) {
        Objects.requireNonNull(issuedAt, "issuedAt");
        Instant expiresAt = issuedAt.plus(configuration.requiredTtl());
        MagicLinkToken token = MagicLinkToken.issue(email, hasher.hash(rawToken), issuedAt, expiresAt);
        repository.persist(token);
        return token;
    }

    /**
     * Verifica se o token é válido no instante informado sem consumir.
     * Usado no fluxo de verify para checar antes de decidir se há conta ou não.
     *
     * @param rawToken   token bruto do magic link
     * @param checkedAt  instante de verificação
     * @return token válido, ou vazio se inválido/expirado/já consumido
     */
    public Optional<MagicLinkToken> peekByRawToken(String rawToken, Instant checkedAt) {
        Objects.requireNonNull(checkedAt, "checkedAt");
        String tokenHash = hasher.hash(rawToken);
        Optional<MagicLinkToken> candidate = repository.findByTokenHash(tokenHash);
        if (candidate.isEmpty() || !candidate.get().isUsableAt(checkedAt)) {
            return Optional.empty();
        }
        return candidate;
    }

    /**
     * Consome o link uma única vez. Link expirado, inexistente ou já consumido
     * não revela qual estado falhou e não cria qualquer escrita de conta.
     */
    @Transactional
    public Optional<MagicLinkToken> consume(String rawToken, Instant consumedAt) {
        Objects.requireNonNull(consumedAt, "consumedAt");
        String tokenHash = hasher.hash(rawToken);
        Optional<MagicLinkToken> candidate = repository.findByTokenHash(tokenHash);
        if (candidate.isEmpty() || !candidate.get().isUsableAt(consumedAt)) {
            return Optional.empty();
        }

        if (repository.consumeIfAvailable(tokenHash, consumedAt) != 1) {
            return Optional.empty();
        }

        MagicLinkToken consumed = candidate.get();
        consumed.markConsumed(consumedAt);
        return Optional.of(consumed);
    }
}
