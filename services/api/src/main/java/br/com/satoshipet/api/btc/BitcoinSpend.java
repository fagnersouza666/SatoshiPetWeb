package br.com.satoshipet.api.btc;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Registro de que uma saída Bitcoin foi gasta por outra transação. */
@Entity
@Table(name = "bitcoin_spends")
public class BitcoinSpend extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Saída que foi gasta. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "output_id", nullable = false, unique = true, updatable = false)
    public BitcoinOutput output;

    /** Txid da transação que gastou esta saída. */
    @Column(name = "spending_txid", nullable = false, length = 64, updatable = false)
    public String spendingTxid;

    /** Momento em que o gasto foi observado pelo indexador. */
    @Column(name = "observed_at", nullable = false, updatable = false)
    public Instant observedAt;

    protected BitcoinSpend() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um registro de gasto. */
    public static BitcoinSpend create(
            BitcoinOutput output, String spendingTxid, Instant observedAt
    ) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(spendingTxid, "spendingTxid");
        Objects.requireNonNull(observedAt, "observedAt");

        BitcoinSpend spend = new BitcoinSpend();
        spend.id = UUID.randomUUID();
        spend.output = output;
        spend.spendingTxid = spendingTxid;
        spend.observedAt = observedAt;
        return spend;
    }

    /** Verifica se uma saída já foi gasta. */
    public static Optional<BitcoinSpend> findByOutput(BitcoinOutput output) {
        return find("output", output).firstResultOptional();
    }
}
