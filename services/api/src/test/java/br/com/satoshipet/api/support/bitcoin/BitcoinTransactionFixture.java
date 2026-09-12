package br.com.satoshipet.api.support.bitcoin;

import java.time.Instant;
import java.util.List;

/**
 * Dados determinísticos para os cenários de ciclo de vida de uma transação em
 * regtest.
 *
 * <p>A fixture representa a mesma transação em três observações sucessivas.
 * Assim, testes de emissão, mempool e confirmação podem verificar a transição
 * sem depender de relógio, aleatoriedade ou fundos reais.</p>
 */
public final class BitcoinTransactionFixture {

    public static final String NETWORK = "regtest";
    public static final String ADDRESS = "bcrt1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";
    public static final String TXID = "a1".repeat(32);
    public static final String BLOCK_HASH = "b2".repeat(32);
    public static final int BLOCK_HEIGHT = 101;
    public static final long AMOUNT_SATS = 100_000L;
    public static final Instant EMITTED_AT = Instant.parse("2026-01-01T12:00:00Z");
    public static final Instant MEMPOOL_AT = Instant.parse("2026-01-01T12:00:01Z");
    public static final Instant CONFIRMED_AT = Instant.parse("2026-01-01T12:10:00Z");

    private static final Output RECEIVING_OUTPUT = new Output(0, ADDRESS, AMOUNT_SATS);

    private BitcoinTransactionFixture() {
    }

    /**
     * Retorna a observação no instante em que a transação foi emitida, antes
     * de existir evidência de entrada na mempool.
     */
    public static Snapshot emitted() {
        return snapshot(Phase.EMITTED, EMITTED_AT, null, null, 0);
    }

    /**
     * Retorna a observação da transação pendente na mempool.
     */
    public static Snapshot mempool() {
        return snapshot(Phase.MEMPOOL, MEMPOOL_AT, null, null, 0);
    }

    /**
     * Retorna a observação da mesma transação após sua inclusão em bloco.
     */
    public static Snapshot confirmed() {
        return snapshot(Phase.CONFIRMED, CONFIRMED_AT, BLOCK_HASH, BLOCK_HEIGHT, 1);
    }

    /**
     * Retorna o ciclo completo, na ordem em que as observações acontecem.
     */
    public static List<Snapshot> lifecycle() {
        return List.of(emitted(), mempool(), confirmed());
    }

    private static Snapshot snapshot(
            Phase phase,
            Instant observedAt,
            String blockHash,
            Integer blockHeight,
            int confirmations
    ) {
        return new Snapshot(
                NETWORK,
                TXID,
                phase,
                List.of(RECEIVING_OUTPUT),
                observedAt,
                blockHash,
                blockHeight,
                confirmations
        );
    }

    public enum Phase {
        EMITTED,
        MEMPOOL,
        CONFIRMED
    }

    public record Snapshot(
            String network,
            String txid,
            Phase phase,
            List<Output> outputs,
            Instant observedAt,
            String blockHash,
            Integer blockHeight,
            int confirmations
    ) {
        public Snapshot {
            outputs = List.copyOf(outputs);
        }
    }

    public record Output(int vout, String address, long amountSats) {
    }
}
