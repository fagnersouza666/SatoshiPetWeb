package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PublicBalanceProjectionTest {
    @Inject PublicAddressResource resource;
    @Inject BitcoinMonitorService monitor;
    @Inject StubBitcoinIndexer indexer;

    @Test
    @TestTransaction
    void saldoNaoConsultadoNaoEZero() {
        Address address = Address.create(BitcoinTestAddresses.MAINNET_UNMONITORED, Instant.now());
        address.persist();
        PublicAddressResponse response = (PublicAddressResponse) resource.getAddress(address.canonical).getEntity();
        assertNull(response.confirmedSats());
    }

    @Test
    @TestTransaction
    void saldoLiquidoDepoisDeGastarTudoNaoSomaRecebimentos() {
        Address address = Address.create(BitcoinTestAddresses.MAINNET_UNMONITORED, Instant.now());
        address.persist();
        LogicalReceipt receipt = LogicalReceipt.createPending(address, "ef".repeat(32), 100_000L, Instant.now());
        receipt.confirmedSats = 100_000L;
        receipt.pendingSats = 0L;
        receipt.persist();
        indexer.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 0L, 0L));
        monitor.pollAddress(address);

        PublicAddressResponse response = (PublicAddressResponse) resource.getAddress(address.canonical).getEntity();
        assertEquals(0L, response.confirmedSats());
    }
}
