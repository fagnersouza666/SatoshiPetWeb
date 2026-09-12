package br.com.satoshipet.api.support.bitcoin;

import java.time.Instant;
import java.util.List;

/**
 * Dados determinísticos para uma reorganização de chain em regtest.
 *
 * <p>A transação confirma na cadeia original, sai do bloco durante a
 * reorganização e volta à mempool antes de confirmar na nova cadeia. O evento
 * mantém os hashes dos blocos afetados, enquanto cada observação expõe o
 * saldo confirmado e as confirmações correspondentes ao estado atual.</p>
 */
public final class BitcoinReorgFixture {

    public static final String NETWORK = "regtest";
    public static final String ADDRESS = BitcoinTransactionFixture.ADDRESS;
    public static final String LOGICAL_RECEIPT_ID = "logical-receipt-reorg-001";
    public static final String TXID = "f6".repeat(32);
    public static final String OLD_BLOCK_HASH = "a7".repeat(32);
    public static final String NEW_TIP_HASH = "b8".repeat(32);
    public static final String NEW_BLOCK_HASH = "c9".repeat(32);
    public static final int OLD_BLOCK_HEIGHT = 200;
    public static final int NEW_TIP_HEIGHT = 202;
    public static final int NEW_BLOCK_HEIGHT = 201;
    public static final long RECEIVED_SATS = 100_000L;
    public static final Instant ORIGINAL_CONFIRMATION_AT = Instant.parse("2026-01-03T12:10:00Z");
    public static final Instant REORG_AT = Instant.parse("2026-01-03T12:20:00Z");
    public static final Instant NEW_CONFIRMATION_AT = Instant.parse("2026-01-03T12:30:00Z");
    public static final String REORG_EVENT_ID = "chain-reorg-regtest-001";

    private BitcoinReorgFixture() {
    }

    /**
     * Retorna a transação confirmada no bloco que será removido da cadeia
     * ativa.
     */
    public static Observation confirmedOnOriginalChain() {
        return new Observation(
                NETWORK,
                LOGICAL_RECEIPT_ID,
                TXID,
                Event.CONFIRMED,
                Status.CONFIRMED,
                OLD_BLOCK_HASH,
                OLD_BLOCK_HEIGHT,
                3,
                RECEIVED_SATS,
                ORIGINAL_CONFIRMATION_AT,
                null
        );
    }

    /**
     * Retorna a situação imediatamente após o reorg. A transação permanece
     * conhecida e volta à mempool; portanto, não é marcada como descarte.
     */
    public static Observation returnedToMempoolAfterReorg() {
        return new Observation(
                NETWORK,
                LOGICAL_RECEIPT_ID,
                TXID,
                Event.CHAIN_REORG,
                Status.MEMPOOL,
                null,
                null,
                0,
                0L,
                REORG_AT,
                OLD_BLOCK_HASH
        );
    }

    /**
     * Retorna a mesma transação confirmada em um bloco da nova cadeia.
     */
    public static Observation confirmedOnReplacementChain() {
        return new Observation(
                NETWORK,
                LOGICAL_RECEIPT_ID,
                TXID,
                Event.CONFIRMED,
                Status.CONFIRMED,
                NEW_BLOCK_HASH,
                NEW_BLOCK_HEIGHT,
                2,
                RECEIVED_SATS,
                NEW_CONFIRMATION_AT,
                OLD_BLOCK_HASH
        );
    }

    /**
     * Retorna o evento de reorganização com a sua trilha de blocos afetados.
     */
    public static ReorgEvent reorg() {
        return new ReorgEvent(
                REORG_EVENT_ID,
                NETWORK,
                OLD_BLOCK_HASH,
                NEW_TIP_HASH,
                List.of(OLD_BLOCK_HASH),
                List.of(NEW_TIP_HASH, NEW_BLOCK_HASH),
                List.of(TXID),
                REORG_AT
        );
    }

    /**
     * Retorna as observações do ciclo completo da reorganização.
     */
    public static List<Observation> lifecycle() {
        return List.of(
                confirmedOnOriginalChain(),
                returnedToMempoolAfterReorg(),
                confirmedOnReplacementChain()
        );
    }

    public enum Event {
        CONFIRMED,
        CHAIN_REORG
    }

    public enum Status {
        MEMPOOL,
        CONFIRMED
    }

    public record Observation(
            String network,
            String logicalReceiptId,
            String txid,
            Event event,
            Status status,
            String blockHash,
            Integer blockHeight,
            int confirmations,
            long confirmedBalanceSats,
            Instant observedAt,
            String previousBlockHash
    ) {
    }

    public record ReorgEvent(
            String eventId,
            String network,
            String oldTipHash,
            String newTipHash,
            List<String> detachedBlockHashes,
            List<String> attachedBlockHashes,
            List<String> affectedTxids,
            Instant observedAt
    ) {
        public ReorgEvent {
            detachedBlockHashes = List.copyOf(detachedBlockHashes);
            attachedBlockHashes = List.copyOf(attachedBlockHashes);
            affectedTxids = List.copyOf(affectedTxids);
        }
    }
}
