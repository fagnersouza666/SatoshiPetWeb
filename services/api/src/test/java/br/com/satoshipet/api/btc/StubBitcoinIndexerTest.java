package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.support.bitcoin.BitcoinTransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários para {@link StubBitcoinIndexer}.
 *
 * <p>Valida que a API de configuração do stub funciona corretamente
 * e que os dados de fixture são retornados conforme esperado.</p>
 */
class StubBitcoinIndexerTest {

    private StubBitcoinIndexer stub;

    @BeforeEach
    void setUp() {
        stub = new StubBitcoinIndexer();
    }

    // -------------------------------------------------------------------------
    // Saldo padrão (sem configuração)
    // -------------------------------------------------------------------------

    @Test
    void saldoPadraoEhUnknown() {
        BitcoinIndexerPort.BalanceResult result = stub.getBalance("bc1qtest");

        assertEquals(BitcoinIndexerPort.BalanceState.UNKNOWN, result.state());
        assertEquals(0L, result.confirmedSats());
        assertEquals(0L, result.pendingSats());
    }

    @Test
    void transacoesPadraoSaoVazias() {
        assertTrue(stub.getTransactions("bc1qtest", null, 25).isEmpty());
        assertTrue(stub.getMempool("bc1qtest").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Configuração de saldo
    // -------------------------------------------------------------------------

    @Test
    void saldoConfiguradoRetornaCorretamente() {
        var expected = new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 100_000L, 0L
        );
        stub.setBalance("bc1qtest", expected);

        BitcoinIndexerPort.BalanceResult result = stub.getBalance("bc1qtest");

        assertEquals(BitcoinIndexerPort.BalanceState.CONFIRMED, result.state());
        assertEquals(100_000L, result.confirmedSats());
    }

    @Test
    void saldoEhCase_insensitive() {
        var expected = new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 200_000L, 50_000L
        );
        stub.setBalance("BC1QTEST", expected);

        // Deve retornar mesmo que a chave seja passada em lowercase
        BitcoinIndexerPort.BalanceResult result = stub.getBalance("bc1qtest");
        assertEquals(200_000L, result.confirmedSats());
    }

    // -------------------------------------------------------------------------
    // Transações on-chain
    // -------------------------------------------------------------------------

    @Test
    void transacaoAdicionadaRetornaCorretamente() {
        BitcoinIndexerPort.TransactionInfo txInfo = new BitcoinIndexerPort.TransactionInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinTransactionFixture.CONFIRMED_AT,
                BitcoinTransactionFixture.CONFIRMED_AT,
                BitcoinTransactionFixture.BLOCK_HEIGHT,
                BitcoinTransactionFixture.BLOCK_HASH,
                List.of(new BitcoinIndexerPort.OutputInfo(
                        0, BitcoinTransactionFixture.ADDRESS, BitcoinTransactionFixture.AMOUNT_SATS
                ))
        );

        stub.addTransaction(BitcoinTransactionFixture.ADDRESS, txInfo);

        List<BitcoinIndexerPort.TransactionInfo> result =
                stub.getTransactions(BitcoinTransactionFixture.ADDRESS, null, 25);

        assertEquals(1, result.size());
        assertEquals(BitcoinTransactionFixture.TXID, result.get(0).txid());
        assertEquals(BitcoinTransactionFixture.AMOUNT_SATS, result.get(0).amountSats());
        assertEquals(BitcoinTransaction.Status.CONFIRMED, result.get(0).status());
    }

    @Test
    void paginacaoPorCursorRetornaCorreto() {
        String addr = "bc1qtest";
        for (int i = 1; i <= 5; i++) {
            stub.addTransaction(addr, new BitcoinIndexerPort.TransactionInfo(
                    "txid-" + i, 10_000L * i, BitcoinTransaction.Status.CONFIRMED,
                    null, null, null, null, List.of()
            ));
        }

        // Cursor em "txid-2" → deve retornar txid-3, txid-4, txid-5
        List<BitcoinIndexerPort.TransactionInfo> result =
                stub.getTransactions(addr, "txid-2", 10);

        assertEquals(3, result.size());
        assertEquals("txid-3", result.get(0).txid());
        assertEquals("txid-5", result.get(2).txid());
    }

    @Test
    void limitePaginacaoRespeita() {
        String addr = "bc1qtest";
        for (int i = 1; i <= 10; i++) {
            stub.addTransaction(addr, new BitcoinIndexerPort.TransactionInfo(
                    "txid-" + i, 1_000L, BitcoinTransaction.Status.CONFIRMED,
                    null, null, null, null, List.of()
            ));
        }

        List<BitcoinIndexerPort.TransactionInfo> result =
                stub.getTransactions(addr, null, 3);

        assertEquals(3, result.size());
    }

    // -------------------------------------------------------------------------
    // Transações de mempool
    // -------------------------------------------------------------------------

    @Test
    void transacaoMempoolAdicionadaRetornaCorretamente() {
        BitcoinIndexerPort.TransactionInfo pending = new BitcoinIndexerPort.TransactionInfo(
                "mempool-txid", 50_000L, BitcoinTransaction.Status.PENDING,
                BitcoinTransactionFixture.MEMPOOL_AT, null, null, null,
                List.of(new BitcoinIndexerPort.OutputInfo(
                        0, BitcoinTransactionFixture.ADDRESS, 50_000L
                ))
        );

        stub.addMempoolTransaction(BitcoinTransactionFixture.ADDRESS, pending);

        List<BitcoinIndexerPort.TransactionInfo> result =
                stub.getMempool(BitcoinTransactionFixture.ADDRESS);

        assertEquals(1, result.size());
        assertEquals("mempool-txid", result.get(0).txid());
        assertEquals(BitcoinTransaction.Status.PENDING, result.get(0).status());
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    @Test
    void resetLimpaTodasAsConfiguracoes() {
        stub.setBalance("bc1qtest", new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 999_999L, 0L
        ));
        stub.addTransaction("bc1qtest", new BitcoinIndexerPort.TransactionInfo(
                "some-txid", 1L, BitcoinTransaction.Status.CONFIRMED,
                null, null, null, null, List.of()
        ));

        stub.reset();

        assertEquals(BitcoinIndexerPort.BalanceState.UNKNOWN, stub.getBalance("bc1qtest").state());
        assertTrue(stub.getTransactions("bc1qtest", null, 25).isEmpty());
    }

    @Test
    void resetAddressLimpaApenasEnderecoEspecifico() {
        stub.setBalance("bc1qa", new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 111L, 0L
        ));
        stub.setBalance("bc1qb", new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 222L, 0L
        ));

        stub.resetAddress("bc1qa");

        assertEquals(BitcoinIndexerPort.BalanceState.UNKNOWN, stub.getBalance("bc1qa").state());
        assertEquals(222L, stub.getBalance("bc1qb").confirmedSats());
    }

    // -------------------------------------------------------------------------
    // PROVIDER_FAILURE
    // -------------------------------------------------------------------------

    @Test
    void stubPodeRetornarProviderFailure() {
        stub.setBalance("bc1qtest", new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE, 0L, 0L
        ));

        assertEquals(BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE,
                stub.getBalance("bc1qtest").state());
    }
}
