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

    @Test
    @TestTransaction
    void respostaPublicaExpoeSomenteAtlasDaArteAprovada() {
        Instant now = Instant.now();
        Address address = Address.create(BitcoinTestAddresses.RECONCILIATION_FIRST, now);
        address.persist();
        var account = br.com.satoshipet.api.account.Account.create(
                "atlas-public-" + java.util.UUID.randomUUID() + "@test.invalid", "Etc/UTC", "pt-BR", now);
        account.persist();
        var pet = br.com.satoshipet.api.pet.Pet.create(address, account, "Atlas público", now);
        pet.artworkStatus = br.com.satoshipet.api.pet.ArtworkStatus.APPROVED;
        pet.persist();
        var context = new br.com.satoshipet.api.art.FrozenGenerationContext(
                address.canonical, "Etc/UTC", null, null, "day", now.toString());
        var artwork = br.com.satoshipet.api.art.PetArtwork.createPending(pet, context, "privado", now);
        artwork.assetVersion = 3;
        artwork.generationStatus = br.com.satoshipet.api.art.ArtGenerationStatus.AWAITING_APPROVAL;
        artwork.persist();

        PublicAddressResponse preview = (PublicAddressResponse) resource.getAddress(address.canonical).getEntity();
        assertNull(preview.atlasUrl(), "prévia aguardando aprovação não pode vazar no DTO público");
        assertNull(preview.artworkVersion());

        artwork.generationStatus = br.com.satoshipet.api.art.ArtGenerationStatus.APPROVED;
        PublicAddressResponse approved = (PublicAddressResponse) resource.getAddress(address.canonical).getEntity();
        assertEquals("3", approved.artworkVersion());
        assertEquals("/api/v1/public/addresses/" + address.canonical + "/artwork/3/atlas.png", approved.atlasUrl());
    }

    @Test
    @TestTransaction
    void historicoPublicoLimitaVinteSemPerderContagemEOrdem() {
        Instant now = Instant.now();
        Address address = Address.create(BitcoinTestAddresses.RECONCILIATION_SECOND, now);
        address.persist();
        for (int i = 1; i <= 25; i++) {
            BitcoinTransaction.createPending(String.format("%064x", i), address, i,
                    now.minusSeconds(25 - i)).persist();
        }

        PublicAddressResponse response = (PublicAddressResponse) resource.getAddress(address.canonical).getEntity();
        assertEquals(25, response.transactionCount());
        assertEquals(20, response.recentTransactions().size());
        for (int i = 0; i < 20; i++) {
            assertEquals(String.format("%064x", 25 - i), response.recentTransactions().get(i).txid(),
                    "histórico deve estar ordenado do mais recente para o mais antigo");
        }
    }
}
