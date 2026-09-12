package br.com.satoshipet.api.support.bitcoin;

import java.time.Instant;
import java.util.List;

/**
 * Dados determinísticos para substituição por RBF e descarte de transação em
 * regtest.
 *
 * <p>As observações usam a mesma entrada gasta e o mesmo recebimento lógico.
 * A substituta altera o valor da saída destinada ao endereço, mas não cria um
 * segundo recebimento. O caminho de descarte preserva a observação original
 * para que a reconstrução cronológica possa invalidar seu efeito.</p>
 */
public final class BitcoinRbfFixture {

    public static final String NETWORK = "regtest";
    public static final String ADDRESS = BitcoinTransactionFixture.ADDRESS;
    public static final String LOGICAL_RECEIPT_ID = "logical-receipt-rbf-001";
    public static final String SPENT_INPUT_TXID = "c3".repeat(32);
    public static final int SPENT_INPUT_VOUT = 1;
    public static final long SPENT_INPUT_AMOUNT_SATS = 120_000L;
    public static final String ORIGINAL_TXID = "d4".repeat(32);
    public static final String REPLACEMENT_TXID = "e5".repeat(32);
    public static final long ORIGINAL_RECEIVED_SATS = 100_000L;
    public static final long REPLACEMENT_RECEIVED_SATS = 80_000L;
    public static final long ORIGINAL_FEE_SATS = 20_000L;
    public static final long REPLACEMENT_FEE_SATS = 40_000L;
    public static final Instant ORIGINAL_MEMPOOL_AT = Instant.parse("2026-01-02T12:00:00Z");
    public static final Instant REPLACEMENT_AT = Instant.parse("2026-01-02T12:00:05Z");
    public static final Instant ORIGINAL_REPLACED_AT = Instant.parse("2026-01-02T12:00:06Z");
    public static final Instant ORIGINAL_DROPPED_AT = Instant.parse("2026-01-02T12:30:00Z");

    private static final Input SPENT_INPUT = new Input(
            SPENT_INPUT_TXID,
            SPENT_INPUT_VOUT,
            SPENT_INPUT_AMOUNT_SATS
    );
    private static final Output ORIGINAL_OUTPUT = new Output(0, ADDRESS, ORIGINAL_RECEIVED_SATS);
    private static final Output REPLACEMENT_OUTPUT = new Output(0, ADDRESS, REPLACEMENT_RECEIVED_SATS);

    private BitcoinRbfFixture() {
    }

    /**
     * Retorna a transação original enquanto ainda está na mempool.
     */
    public static Observation originalMempool() {
        return observation(
                ORIGINAL_TXID,
                State.MEMPOOL,
                List.of(ORIGINAL_OUTPUT),
                ORIGINAL_MEMPOOL_AT,
                null,
                null,
                ORIGINAL_FEE_SATS
        );
    }

    /**
     * Retorna a observação histórica da transação original depois do RBF.
     */
    public static Observation originalReplaced() {
        return observation(
                ORIGINAL_TXID,
                State.REPLACED,
                List.of(ORIGINAL_OUTPUT),
                ORIGINAL_REPLACED_AT,
                REPLACEMENT_TXID,
                null,
                ORIGINAL_FEE_SATS
        );
    }

    /**
     * Retorna a substituta na mempool, com a mesma entrada e valor de
     * recebimento diferente.
     */
    public static Observation replacementMempool() {
        return observation(
                REPLACEMENT_TXID,
                State.MEMPOOL,
                List.of(REPLACEMENT_OUTPUT),
                REPLACEMENT_AT,
                null,
                ORIGINAL_TXID,
                REPLACEMENT_FEE_SATS
        );
    }

    /**
     * Retorna o caminho de observações para a substituição por RBF.
     */
    public static List<Observation> replacementLifecycle() {
        return List.of(originalMempool(), originalReplaced(), replacementMempool());
    }

    /**
     * Retorna a observação de descarte comprovado da transação original.
     */
    public static Observation originalDropped() {
        return observation(
                ORIGINAL_TXID,
                State.DROPPED,
                List.of(ORIGINAL_OUTPUT),
                ORIGINAL_DROPPED_AT,
                null,
                null,
                ORIGINAL_FEE_SATS,
                "mempool-evicted"
        );
    }

    /**
     * Retorna o caminho de observações usado para invalidar um recebimento
     * descartado.
     */
    public static List<Observation> droppedLifecycle() {
        return List.of(originalMempool(), originalDropped());
    }

    private static Observation observation(
            String txid,
            State state,
            List<Output> outputs,
            Instant observedAt,
            String replacedByTxid,
            String replacesTxid,
            long feeSats
    ) {
        return observation(txid, state, outputs, observedAt, replacedByTxid, replacesTxid, feeSats, null);
    }

    private static Observation observation(
            String txid,
            State state,
            List<Output> outputs,
            Instant observedAt,
            String replacedByTxid,
            String replacesTxid,
            long feeSats,
            String dropReason
    ) {
        return new Observation(
                NETWORK,
                LOGICAL_RECEIPT_ID,
                txid,
                state,
                List.of(SPENT_INPUT),
                outputs,
                observedAt,
                feeSats,
                replacedByTxid,
                replacesTxid,
                dropReason
        );
    }

    public enum State {
        MEMPOOL,
        REPLACED,
        DROPPED
    }

    public record Observation(
            String network,
            String logicalReceiptId,
            String txid,
            State state,
            List<Input> inputs,
            List<Output> outputs,
            Instant observedAt,
            long feeSats,
            String replacedByTxid,
            String replacesTxid,
            String dropReason
    ) {
        public Observation {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }

    public record Input(String txid, int vout, long amountSats) {
    }

    public record Output(int vout, String address, long amountSats) {
    }
}
