package br.com.satoshipet.api.support.bitcoin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BitcoinReorgFixtureTest {

    @Test
    void reorgLifecycleKeepsReceiptAndTransactionIdentity() {
        List<BitcoinReorgFixture.Observation> lifecycle = BitcoinReorgFixture.lifecycle();

        assertEquals(List.of(
                BitcoinReorgFixture.Status.CONFIRMED,
                BitcoinReorgFixture.Status.MEMPOOL,
                BitcoinReorgFixture.Status.CONFIRMED
        ), lifecycle.stream().map(BitcoinReorgFixture.Observation::status).toList());
        assertEquals(1, lifecycle.stream()
                .map(BitcoinReorgFixture.Observation::logicalReceiptId)
                .distinct()
                .count());
        assertEquals(1, lifecycle.stream()
                .map(BitcoinReorgFixture.Observation::txid)
                .distinct()
                .count());
    }

    @Test
    void reorgRemovesOldBlockEvidenceAndReturnsTransactionToMempool() {
        BitcoinReorgFixture.Observation before = BitcoinReorgFixture.confirmedOnOriginalChain();
        BitcoinReorgFixture.Observation after = BitcoinReorgFixture.returnedToMempoolAfterReorg();

        assertEquals(BitcoinReorgFixture.OLD_BLOCK_HASH, before.blockHash());
        assertEquals(3, before.confirmations());
        assertEquals(BitcoinReorgFixture.RECEIVED_SATS, before.confirmedBalanceSats());
        assertEquals(BitcoinReorgFixture.Event.CHAIN_REORG, after.event());
        assertEquals(BitcoinReorgFixture.Status.MEMPOOL, after.status());
        assertNull(after.blockHash());
        assertNull(after.blockHeight());
        assertEquals(0, after.confirmations());
        assertEquals(0L, after.confirmedBalanceSats());
        assertEquals(BitcoinReorgFixture.OLD_BLOCK_HASH, after.previousBlockHash());
    }

    @Test
    void reconfirmationUsesNewBlockAndRestoresBalanceWithoutNewReceipt() {
        BitcoinReorgFixture.Observation before = BitcoinReorgFixture.confirmedOnOriginalChain();
        BitcoinReorgFixture.Observation after = BitcoinReorgFixture.confirmedOnReplacementChain();

        assertNotEquals(before.blockHash(), after.blockHash());
        assertEquals(BitcoinReorgFixture.NEW_BLOCK_HASH, after.blockHash());
        assertEquals(BitcoinReorgFixture.NEW_BLOCK_HEIGHT, after.blockHeight());
        assertEquals(2, after.confirmations());
        assertEquals(BitcoinReorgFixture.RECEIVED_SATS, after.confirmedBalanceSats());
        assertEquals(before.logicalReceiptId(), after.logicalReceiptId());
        assertEquals(before.txid(), after.txid());
    }

    @Test
    void reorgEventPreservesAffectedBlocksAndTransaction() {
        BitcoinReorgFixture.ReorgEvent event = BitcoinReorgFixture.reorg();

        assertEquals(BitcoinReorgFixture.REORG_EVENT_ID, event.eventId());
        assertEquals(BitcoinReorgFixture.OLD_BLOCK_HASH, event.oldTipHash());
        assertEquals(BitcoinReorgFixture.NEW_TIP_HASH, event.newTipHash());
        assertEquals(List.of(BitcoinReorgFixture.OLD_BLOCK_HASH), event.detachedBlockHashes());
        assertEquals(List.of(BitcoinReorgFixture.NEW_TIP_HASH, BitcoinReorgFixture.NEW_BLOCK_HASH), event.attachedBlockHashes());
        assertEquals(List.of(BitcoinReorgFixture.TXID), event.affectedTxids());
    }

    @Test
    void repeatedCallsProduceEqualValues() {
        assertEquals(BitcoinReorgFixture.lifecycle(), BitcoinReorgFixture.lifecycle());
        assertEquals(BitcoinReorgFixture.reorg(), BitcoinReorgFixture.reorg());
    }
}
