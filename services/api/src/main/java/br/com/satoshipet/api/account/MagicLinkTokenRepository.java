package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Acesso transacional aos registros de magic link. */
@ApplicationScoped
public class MagicLinkTokenRepository implements PanacheRepositoryBase<MagicLinkToken, UUID> {

    public Optional<MagicLinkToken> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    /**
     * Marca um link como consumido apenas se ele ainda estiver disponível.
     * O predicado torna a operação compare-and-set no banco e evita duplo uso
     * quando duas requisições concorrem pelo mesmo segredo.
     */
    public long consumeIfAvailable(String tokenHash, Instant consumedAt) {
        return update(
                "consumedAt = ?1 WHERE tokenHash = ?2 "
                        + "AND consumedAt IS NULL AND issuedAt <= ?3 AND expiresAt > ?3",
                consumedAt,
                tokenHash,
                consumedAt
        );
    }
}
