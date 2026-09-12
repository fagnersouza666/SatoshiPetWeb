package br.com.satoshipet.api.support.bitcoin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BitcoinTransactionFixtureTest {

    @Test
    void lifecycleKeepsTransactionAndOutputIdentityAcrossPhases() {
        List<BitcoinTransactionFixture.Snapshot> lifecycle = BitcoinTransactionFixture.lifecycle();

        assertEquals(List.of(
                BitcoinTransactionFixture.Phase.EMITTED,
                BitcoinTransactionFixture.Phase.MEMPOOL,
                BitcoinTransactionFixture.Phase.CONFIRMED
        ), lifecycle.stream().map(BitcoinTransactionFixture.Snapshot::phase).toList());
        assertEquals(3, lifecycle.size());
        assertEquals(
                lifecycle.get(0).txid(),
                lifecycle.get(1).txid(),
                "a transação deve manter o mesmo txid ao confirmar"
        );
        assertEquals(lifecycle.get(1).txid(), lifecycle.get(2).txid());
        assertEquals(lifecycle.get(0).outputs(), lifecycle.get(1).outputs());
        assertEquals(lifecycle.get(1).outputs(), lifecycle.get(2).outputs());
    }

    @Test
    void emittedAndMempoolSnapshotsHaveNoBlockOrConfirmation() {
        BitcoinTransactionFixture.Snapshot emitted = BitcoinTransactionFixture.emitted();
        BitcoinTransactionFixture.Snapshot mempool = BitcoinTransactionFixture.mempool();

        assertEquals(0, emitted.confirmations());
        assertEquals(0, mempool.confirmations());
        assertNull(emitted.blockHash());
        assertNull(emitted.blockHeight());
        assertNull(mempool.blockHash());
        assertNull(mempool.blockHeight());
    }

    @Test
    void confirmedSnapshotHasDeterministicBlockEvidence() {
        BitcoinTransactionFixture.Snapshot confirmed = BitcoinTransactionFixture.confirmed();

        assertEquals(BitcoinTransactionFixture.NETWORK, confirmed.network());
        assertEquals(BitcoinTransactionFixture.TXID, confirmed.txid());
        assertEquals(BitcoinTransactionFixture.BLOCK_HASH, confirmed.blockHash());
        assertEquals(BitcoinTransactionFixture.BLOCK_HEIGHT, confirmed.blockHeight());
        assertEquals(1, confirmed.confirmations());
        assertEquals(BitcoinTransactionFixture.AMOUNT_SATS, confirmed.outputs().get(0).amountSats());
    }

    @Test
    void repeatedCallsProduceEqualValues() {
        assertEquals(BitcoinTransactionFixture.emitted(), BitcoinTransactionFixture.emitted());
        assertEquals(BitcoinTransactionFixture.mempool(), BitcoinTransactionFixture.mempool());
        assertEquals(BitcoinTransactionFixture.confirmed(), BitcoinTransactionFixture.confirmed());
    }
}
