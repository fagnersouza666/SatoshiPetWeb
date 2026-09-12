package br.com.satoshipet.api.support.bitcoin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BitcoinRbfFixtureTest {

    @Test
    void replacementKeepsLogicalReceiptAndSpentInputButChangesTransactionAndValue() {
        BitcoinRbfFixture.Observation original = BitcoinRbfFixture.originalMempool();
        BitcoinRbfFixture.Observation replacement = BitcoinRbfFixture.replacementMempool();

        assertEquals(original.logicalReceiptId(), replacement.logicalReceiptId());
        assertNotEquals(original.txid(), replacement.txid());
        assertEquals(original.inputs(), replacement.inputs());
        assertNotEquals(
                original.outputs().get(0).amountSats(),
                replacement.outputs().get(0).amountSats()
        );
        assertEquals(BitcoinRbfFixture.ORIGINAL_FEE_SATS, original.feeSats());
        assertEquals(BitcoinRbfFixture.REPLACEMENT_FEE_SATS, replacement.feeSats());
        assertEquals(
                BitcoinRbfFixture.SPENT_INPUT_AMOUNT_SATS,
                original.outputs().get(0).amountSats() + original.feeSats()
        );
        assertEquals(
                BitcoinRbfFixture.SPENT_INPUT_AMOUNT_SATS,
                replacement.outputs().get(0).amountSats() + replacement.feeSats()
        );
        assertEquals(BitcoinRbfFixture.ORIGINAL_TXID, replacement.replacesTxid());
        assertEquals(BitcoinRbfFixture.REPLACEMENT_TXID, BitcoinRbfFixture.originalReplaced().replacedByTxid());
    }

    @Test
    void replacementLifecycleHasOneLogicalReceiptAndNoSecondFeedingIdentity() {
        List<BitcoinRbfFixture.Observation> lifecycle = BitcoinRbfFixture.replacementLifecycle();

        assertEquals(List.of(
                BitcoinRbfFixture.State.MEMPOOL,
                BitcoinRbfFixture.State.REPLACED,
                BitcoinRbfFixture.State.MEMPOOL
        ), lifecycle.stream().map(BitcoinRbfFixture.Observation::state).toList());
        assertEquals(1, lifecycle.stream()
                .map(BitcoinRbfFixture.Observation::logicalReceiptId)
                .distinct()
                .count());
    }

    @Test
    void droppedLifecyclePreservesHistoryAndProvidesDropEvidence() {
        List<BitcoinRbfFixture.Observation> lifecycle = BitcoinRbfFixture.droppedLifecycle();
        BitcoinRbfFixture.Observation dropped = BitcoinRbfFixture.originalDropped();

        assertEquals(List.of(
                BitcoinRbfFixture.State.MEMPOOL,
                BitcoinRbfFixture.State.DROPPED
        ), lifecycle.stream().map(BitcoinRbfFixture.Observation::state).toList());
        assertEquals(BitcoinRbfFixture.ORIGINAL_TXID, dropped.txid());
        assertEquals(BitcoinRbfFixture.LOGICAL_RECEIPT_ID, dropped.logicalReceiptId());
        assertEquals(BitcoinRbfFixture.ORIGINAL_RECEIVED_SATS, dropped.outputs().get(0).amountSats());
        assertEquals("mempool-evicted", dropped.dropReason());
        assertNull(dropped.replacedByTxid());
    }

    @Test
    void repeatedCallsProduceEqualValues() {
        assertEquals(BitcoinRbfFixture.replacementLifecycle(), BitcoinRbfFixture.replacementLifecycle());
        assertEquals(BitcoinRbfFixture.droppedLifecycle(), BitcoinRbfFixture.droppedLifecycle());
    }
}
