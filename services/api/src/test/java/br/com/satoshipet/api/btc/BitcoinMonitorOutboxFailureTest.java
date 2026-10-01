package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.outbox.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercita a fronteira CDI real: falha no evento deve reverter toda a transação. */
@QuarkusTest
class BitcoinMonitorOutboxFailureTest {

    private static final Instant BEFORE = Instant.parse("2026-09-13T12:00:00Z");
    private static final long SATS = 20_000L;

    @Inject BitcoinMonitorService monitor;
    @Inject StubBitcoinIndexer indexer;
    @Inject ObjectMapper mapper;

    static Stream<Arguments> falhas() {
        return Stream.of("BITCOIN_TRANSACTION_OBSERVED", "BITCOIN_TRANSACTION_CONFIRMED",
                        "BITCOIN_TRANSACTION_REPLACED", "BITCOIN_TRANSACTION_DROPPED",
                        "BITCOIN_CHAIN_REORG", "BITCOIN_BALANCE_RECONCILED")
                .flatMap(type -> Stream.of(Arguments.of(type, false), Arguments.of(type, true)));
    }

    @ParameterizedTest(name = "{0}, falha na persistência={1}")
    @MethodSource("falhas")
    void falhaDoEventoReverteTransacaoRecebimentoCursorEOutbox(String eventType, boolean persistence) {
        String txid = UUID.randomUUID().toString().replace("-", "").repeat(2);
        String canonical = "bcrt1q" + UUID.randomUUID().toString().replace("-", "");
        boolean reorg = eventType.equals("BITCOIN_CHAIN_REORG");
        boolean initialReceipt = !eventType.equals("BITCOIN_TRANSACTION_OBSERVED")
                && !eventType.equals("BITCOIN_BALANCE_RECONCILED");
        Address address = QuarkusTransaction.requiringNew().call(() -> {
            Address created = Address.create(canonical, BEFORE);
            created.persist();
            AddressMonitorState.init(created, BEFORE).persist();
            if (initialReceipt) {
                BitcoinTransaction tx = BitcoinTransaction.createPending(txid, created, SATS, BEFORE);
                LogicalReceipt receipt = LogicalReceipt.createPending(created, txid, SATS, BEFORE);
                if (reorg) {
                    tx.status = BitcoinTransaction.Status.CONFIRMED;
                    tx.confirmedAt = BEFORE;
                    tx.blockHeight = 42;
                    tx.blockHash = "ab".repeat(32);
                    receipt.confirmedSats = SATS;
                    receipt.pendingSats = 0;
                }
                tx.persist();
                receipt.persist();
            }
            return created;
        });

        IllegalStateException cause = new IllegalStateException("falha simulada no evento " + eventType);
        if (persistence) {
            // As demais gravações continuam reais, inclusive eventos anteriores no mesmo poll.
            OutboxService failing = new OutboxService() {
                @Override
                public void save(UUID id, String aggregateType, String aggregateId,
                                 String type, String payload, String correlationId) {
                    if (eventType.equals(type)) {
                        throw cause;
                    }
                    super.save(id, aggregateType, aggregateId, type, payload, correlationId);
                }
            };
            QuarkusMock.installMockForType(failing, OutboxService.class);
        } else {
            BitcoinEventRedactor failing = new BitcoinEventRedactor(mapper) {
                @Override
                public String redact(String type, Map<String, Object> payload,
                                     String correlationId, String causationId) {
                    if (eventType.equals(type)) {
                        throw cause;
                    }
                    return super.redact(type, payload, correlationId, causationId);
                }
            };
            QuarkusMock.installMockForType(failing, BitcoinEventRedactor.class);
        }

        BitcoinTransaction.Status next = switch (eventType) {
            case "BITCOIN_TRANSACTION_CONFIRMED" -> BitcoinTransaction.Status.CONFIRMED;
            case "BITCOIN_TRANSACTION_REPLACED" -> BitcoinTransaction.Status.REPLACED;
            case "BITCOIN_TRANSACTION_DROPPED" -> BitcoinTransaction.Status.DROPPED;
            default -> BitcoinTransaction.Status.PENDING;
        };
        indexer.addMempoolTransaction(canonical, new BitcoinIndexerPort.TransactionInfo(
                txid, SATS, next, BEFORE, next == BitcoinTransaction.Status.CONFIRMED ? BEFORE : null,
                42, "ab".repeat(32), List.of(new BitcoinIndexerPort.OutputInfo(0, canonical, SATS))));
        if (eventType.equals("BITCOIN_BALANCE_RECONCILED")) {
            indexer.setBalance(canonical, new BitcoinIndexerPort.BalanceResult(
                    BitcoinIndexerPort.BalanceState.CONFIRMED, SATS, 0));
        }

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
                if (reorg) {
                    monitor.handleReorg(txid, Map.of(), BEFORE.plusSeconds(60));
                } else {
                    monitor.pollAddress(address);
                }
            });
            assertSame(cause, failure.getCause());

            QuarkusTransaction.requiringNew().run(() -> {
                BitcoinTransaction tx = BitcoinTransaction.findByTxid(txid).orElse(null);
                LogicalReceipt receipt = LogicalReceipt.findByAddressAndTxid(address, txid).orElse(null);
                if (initialReceipt) {
                    assertEquals(reorg ? BitcoinTransaction.Status.CONFIRMED : BitcoinTransaction.Status.PENDING,
                            tx.status);
                    assertEquals(reorg ? SATS : 0, receipt.confirmedSats);
                    assertEquals(reorg ? 0 : SATS, receipt.pendingSats);
                    assertEquals(BEFORE, receipt.updatedAt);
                    assertEquals(reorg ? BEFORE : null, tx.confirmedAt);
                } else {
                    assertNull(tx);
                    assertNull(receipt);
                    assertEquals(0, BitcoinOutput.count("address", address));
                }
                AddressMonitorState state = AddressMonitorState.findByAddress(address).orElseThrow();
                assertNull(state.lastSeenTxid);
                assertNull(state.cursor);
                assertEquals(BEFORE, state.lastCheckedAt);
                assertEquals(0, OutboxEvent.count("aggregateId", canonical));
            });
        } finally {
            indexer.resetAddress(canonical);
            QuarkusTransaction.requiringNew().run(() -> {
                OutboxEvent.delete("aggregateId", canonical);
                BitcoinOutput.delete("address", address);
                LogicalReceipt.delete("address", address);
                BitcoinTransaction.delete("address", address);
                AddressMonitorState.deleteById(address.id);
                Address.deleteById(address.id);
            });
        }
    }
}
