package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transação Bitcoin observada pelo indexador (Blockstream Esplora via
 * {@code BitcoinIndexerPort}).
 * O status reflete o ciclo de vida: PENDING → CONFIRMED / REPLACED / DROPPED.
 */
@Entity
@Table(
        name = "bitcoin_transactions",
        indexes = {
                @Index(name = "idx_bitcoin_transactions_address", columnList = "address_id"),
                @Index(name = "idx_bitcoin_transactions_status",  columnList = "status")
        }
)
public class BitcoinTransaction extends PanacheEntityBase {

    /** Estados possíveis de uma transação Bitcoin. */
    public enum Status {
        PENDING, CONFIRMED, REPLACED, DROPPED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Hash da transação em hexadecimal (64 chars). */
    @Column(name = "txid", nullable = false, length = 64, unique = true, updatable = false)
    public String txid;

    /** Endereço receptor desta observação. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false, updatable = false)
    public Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public Status status;

    /** Valor total recebido nesta transação para o endereço (sats). */
    @Column(name = "amount_sats", nullable = false)
    public long amountSats;

    /** Primeira vez que o indexador observou a transação. */
    @Column(name = "observed_at", nullable = false, updatable = false)
    public Instant observedAt;

    /** Preenchido ao confirmar em bloco. */
    @Column(name = "confirmed_at")
    public Instant confirmedAt;

    /** Altura do bloco de confirmação. */
    @Column(name = "block_height")
    public Integer blockHeight;

    /** Hash do bloco de confirmação (64 chars). */
    @Column(name = "block_hash", length = 64)
    public String blockHash;

    protected BitcoinTransaction() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria observação inicial de uma transação pendente. */
    public static BitcoinTransaction createPending(
            String txid, Address address, long amountSats, Instant observedAt
    ) {
        Objects.requireNonNull(txid, "txid");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(observedAt, "observedAt");

        BitcoinTransaction tx = new BitcoinTransaction();
        tx.id = UUID.randomUUID();
        tx.txid = txid;
        tx.address = address;
        tx.status = Status.PENDING;
        tx.amountSats = amountSats;
        tx.observedAt = observedAt;
        return tx;
    }

    /** Retorna transações pendentes de um endereço. */
    public static List<BitcoinTransaction> findPendingByAddress(Address address) {
        return list("address = ?1 AND status = ?2", address, Status.PENDING);
    }

    /** Busca por txid. */
    public static Optional<BitcoinTransaction> findByTxid(String txid) {
        return find("txid", txid).firstResultOptional();
    }
}
