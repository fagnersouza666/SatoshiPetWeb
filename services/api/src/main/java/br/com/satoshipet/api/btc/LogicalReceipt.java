package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Recebimento lógico: agregado imutável que representa um recebimento real
 * on-chain por (endereço, txid de referência).
 *
 * <p>Reorg e RBF recalculam os saldos confirmados/pendentes sem reescrever
 * o histórico nem duplicar entradas (PRD §9, CC-10).</p>
 */
@Entity
@Table(
        name = "logical_receipts",
        indexes = {
                @Index(name = "idx_logical_receipts_address", columnList = "address_id")
        }
)
public class LogicalReceipt extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Endereço receptor. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false, updatable = false)
    public Address address;

    /** Txid da transação que originou este recebimento. */
    @Column(name = "reference_txid", nullable = false, length = 64, updatable = false)
    public String referenceTxid;

    /** Valor total declarado pelo recebimento (sats). Nunca usa ponto flutuante (CC-10). */
    @Column(name = "amount_sats", nullable = false)
    public long amountSats;

    /** Saldo com pelo menos 1 confirmação. */
    @Column(name = "confirmed_sats", nullable = false)
    public long confirmedSats;

    /** Saldo em mempool (pendente). */
    @Column(name = "pending_sats", nullable = false)
    public long pendingSats;

    /** Momento de criação do registro. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** Momento da última atualização de saldo. */
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected LogicalReceipt() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um recebimento lógico inicial (tudo pendente). */
    public static LogicalReceipt createPending(
            Address address, String referenceTxid, long amountSats, Instant now
    ) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(referenceTxid, "referenceTxid");
        Objects.requireNonNull(now, "now");

        LogicalReceipt receipt = new LogicalReceipt();
        receipt.id = UUID.randomUUID();
        receipt.address = address;
        receipt.referenceTxid = referenceTxid;
        receipt.amountSats = amountSats;
        receipt.confirmedSats = 0L;
        receipt.pendingSats = amountSats;
        receipt.createdAt = now;
        receipt.updatedAt = now;
        return receipt;
    }

    /** Busca recebimento pelo par (endereço, txid). */
    public static Optional<LogicalReceipt> findByAddressAndTxid(Address address, String txid) {
        return find("address = ?1 AND referenceTxid = ?2", address, txid).firstResultOptional();
    }

    /** Lista todos os recebimentos de um endereço. */
    public static List<LogicalReceipt> findByAddress(Address address) {
        return list("address", address);
    }
}
