package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Outpoint consumido por uma observação; identidade de conflitos RBF. */
@Entity
@Table(name = "bitcoin_inputs")
public class BitcoinInput extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    public BitcoinTransaction transaction;
    @Column(name = "previous_txid", nullable = false, length = 64, updatable = false)
    public String previousTxid;
    @Column(name = "previous_vout", nullable = false, updatable = false)
    public int previousVout;

    public static List<BitcoinIndexerPort.InputInfo> inputsOf(BitcoinTransaction tx) {
        return BitcoinInput.<BitcoinInput>list("transaction", tx).stream()
                .map(input -> new BitcoinIndexerPort.InputInfo(input.previousTxid, input.previousVout)).toList();
    }

    public static void record(BitcoinTransaction tx, List<BitcoinIndexerPort.InputInfo> inputs) {
        var recorded = new java.util.HashSet<>(inputsOf(tx));
        for (var input : inputs) {
            if (!recorded.add(input)) continue;
            BitcoinInput entity = new BitcoinInput();
            entity.id = UUID.randomUUID(); entity.transaction = tx;
            entity.previousTxid = input.txid(); entity.previousVout = input.vout(); entity.persist();
        }
    }

    public static List<BitcoinTransaction> conflicts(Address address, List<BitcoinIndexerPort.InputInfo> inputs) {
        var matches = new LinkedHashMap<UUID, BitcoinTransaction>();
        for (var input : inputs) {
            List<BitcoinInput> rows = list("transaction.address = ?1 and previousTxid = ?2 and previousVout = ?3 "
                    + "and transaction.status in (?4, ?5)", address, input.txid(), input.vout(),
                    BitcoinTransaction.Status.PENDING, BitcoinTransaction.Status.CONFIRMED);
            rows.forEach(row -> matches.put(row.transaction.id, row.transaction));
        }
        return matches.values().stream().sorted(java.util.Comparator
                .comparing((BitcoinTransaction tx) -> tx.observedAt).thenComparing(tx -> tx.txid)).toList();
    }
}
