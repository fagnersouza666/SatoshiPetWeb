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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Saída (output/UTXO) de uma transação Bitcoin. */
@Entity
@Table(
        name = "bitcoin_outputs",
        indexes = {
                @Index(name = "idx_bitcoin_outputs_transaction", columnList = "transaction_id"),
                @Index(name = "idx_bitcoin_outputs_address",     columnList = "address_id")
        }
)
public class BitcoinOutput extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Transação à qual esta saída pertence. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    public BitcoinTransaction transaction;

    /** Índice da saída na transação (vout). */
    @Column(name = "output_index", nullable = false, updatable = false)
    public int outputIndex;

    /** Endereço ao qual o valor é destinado. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false, updatable = false)
    public Address address;

    /** Valor desta saída em satoshis. */
    @Column(name = "value_sats", nullable = false, updatable = false)
    public long valueSats;

    /** Script de bloqueio da saída (scriptPubKey em hex). */
    @Column(name = "script", columnDefinition = "TEXT")
    public String script;

    protected BitcoinOutput() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria uma saída com id gerado. */
    public static BitcoinOutput create(
            BitcoinTransaction transaction, int outputIndex, Address address,
            long valueSats, String script
    ) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(address, "address");

        BitcoinOutput output = new BitcoinOutput();
        output.id = UUID.randomUUID();
        output.transaction = transaction;
        output.outputIndex = outputIndex;
        output.address = address;
        output.valueSats = valueSats;
        output.script = script;
        return output;
    }

    /** Retorna as saídas de uma transação. */
    public static List<BitcoinOutput> findByTransaction(BitcoinTransaction tx) {
        return list("transaction", tx);
    }
}
