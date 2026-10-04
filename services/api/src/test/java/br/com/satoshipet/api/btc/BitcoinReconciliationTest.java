package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.*;
import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class BitcoinReconciliationTest {
    @Inject BitcoinMonitorService monitor;
    @Inject StubBitcoinIndexer stub;
    @Inject PetReferencePortionPort portions;
    static final String ORIGINAL = "cd".repeat(32), REPLACEMENT = "ef".repeat(32);
    static final BitcoinIndexerPort.InputInfo INPUT = new BitcoinIndexerPort.InputInfo("ab".repeat(32), 2);

    @BeforeEach void reset() { stub.reset(); }

    @Test @TestTransaction
    void rbfComInputComumPreservaRecebimentoEAlimentacao() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        Pet pet = pet(address);
        pending(address, ORIGINAL, 20_000, INPUT);
        monitor.pollAddress(address);
        UUID receiptId = LogicalReceipt.findByAddress(address).getFirst().id;
        UUID feedingId = PetFeeding.listByPet(pet).getFirst().id;
        stub.resetAddress(address.canonical);
        pending(address, REPLACEMENT, 10_000, INPUT);
        monitor.pollAddress(address);
        monitor.pollAddress(address);
        assertEquals(1, LogicalReceipt.findByAddress(address).size());
        assertEquals(receiptId, LogicalReceipt.findByAddress(address).getFirst().id);
        assertEquals(10_000, LogicalReceipt.findByAddress(address).getFirst().pendingSats);
        assertEquals(REPLACEMENT, BitcoinTransaction.findCurrentByReceipt(
                LogicalReceipt.findByAddress(address).getFirst()).orElseThrow().txid);
        assertEquals(1, PetFeeding.listByPet(pet).size());
        assertEquals(feedingId, PetFeeding.listByPet(pet).getFirst().id);
        assertEquals(10_000, PetFeeding.listByPet(pet).getFirst().amountSats);
        assertEquals(BitcoinTransaction.Status.REPLACED, transaction(address, ORIGINAL).status);
    }

    @Test @TestTransaction
    void mesmoValorSemInputComumSaoDoisRecebimentos() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        pending(address, ORIGINAL, 20_000, INPUT);
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        pending(address, REPLACEMENT, 20_000, new BitcoinIndexerPort.InputInfo("aa".repeat(32), 3));
        monitor.pollAddress(address);
        assertEquals(2, LogicalReceipt.findByAddress(address).size());
        assertEquals(BitcoinTransaction.Status.PENDING, transaction(address, ORIGINAL).status);
    }

    @Test @TestTransaction
    void conflitoForaDaListagemSemSaidaInvalidaRecebimento() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        Pet pet = pet(address);
        pending(address, ORIGINAL, 20_000, INPUT);
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        stub.setOutspend(INPUT, new BitcoinIndexerPort.OutspendLookup(true, REPLACEMENT));
        stub.setTransactionLookup(address.canonical, REPLACEMENT, new BitcoinIndexerPort.TransactionLookup(
                BitcoinIndexerPort.LookupState.FOUND, info(REPLACEMENT, 0, BitcoinTransaction.Status.CONFIRMED, INPUT)));
        monitor.pollAddress(address);
        assertEquals(0, LogicalReceipt.findByAddress(address).getFirst().pendingSats);
        assertEquals(FeedingStatus.INVALIDATED, PetFeeding.listByPet(pet).getFirst().status);
        assertEquals(BitcoinTransaction.Status.REPLACED, transaction(address, ORIGINAL).status);
    }

    @Test @TestTransaction
    void substituicaoPosteriorRestauraMesmaAlimentacaoInvalidada() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        Pet pet = pet(address);
        pending(address, ORIGINAL, 20_000, INPUT);
        monitor.pollAddress(address);
        UUID feedingId = PetFeeding.listByPet(pet).getFirst().id;
        stub.resetAddress(address.canonical);
        stub.setOutspend(INPUT, new BitcoinIndexerPort.OutspendLookup(true, REPLACEMENT));
        stub.setTransactionLookup(address.canonical, REPLACEMENT, new BitcoinIndexerPort.TransactionLookup(
                BitcoinIndexerPort.LookupState.FOUND, info(REPLACEMENT, 0, BitcoinTransaction.Status.PENDING, INPUT)));
        monitor.pollAddress(address);
        assertEquals(FeedingStatus.INVALIDATED, PetFeeding.listByPet(pet).getFirst().status);
        String restored = "01".repeat(32);
        stub.setOutspend(INPUT, new BitcoinIndexerPort.OutspendLookup(true, restored));
        pending(address, restored, 10_000, INPUT);
        monitor.pollAddress(address);
        assertEquals(1, PetFeeding.listByPet(pet).size());
        assertEquals(feedingId, PetFeeding.listByPet(pet).getFirst().id);
        assertEquals(FeedingStatus.PROVISIONAL, PetFeeding.listByPet(pet).getFirst().status);
        assertEquals(10_000, PetFeeding.listByPet(pet).getFirst().amountSats);
    }

    @Test @TestTransaction
    void ausenciaOuFalhaNoOutspendNaoDescartaRecebimento() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        pending(address, ORIGINAL, 20_000, INPUT);
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        monitor.pollAddress(address);
        assertEquals(BitcoinTransaction.Status.PENDING, transaction(address, ORIGINAL).status);
        stub.setOutspend(INPUT, new BitcoinIndexerPort.OutspendLookup(false, null));
        monitor.pollAddress(address);
        assertEquals(20_000, LogicalReceipt.findByAddress(address).getFirst().pendingSats);
        assertFalse(AddressMonitorState.findByAddress(address).orElseThrow().providerAvailable);
    }

    @Test @TestTransaction
    void confirmadaForaDaPrimeiraPaginaVoltaAMempoolPelaConsultaIndividual() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        stub.addTransaction(address.canonical, info(ORIGINAL, 20_000, BitcoinTransaction.Status.CONFIRMED, INPUT));
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        for (int i = 1; i <= 30; i++) stub.addTransaction(address.canonical,
                info(String.format("%064x", i), 1, BitcoinTransaction.Status.CONFIRMED,
                        new BitcoinIndexerPort.InputInfo(String.format("%064x", i + 100), 0)));
        stub.setTransactionLookup(address.canonical, ORIGINAL, new BitcoinIndexerPort.TransactionLookup(
                BitcoinIndexerPort.LookupState.FOUND, info(ORIGINAL, 20_000, BitcoinTransaction.Status.PENDING, INPUT)));
        monitor.pollAddress(address);
        assertEquals(BitcoinTransaction.Status.PENDING, transaction(address, ORIGINAL).status);
        assertEquals(0, LogicalReceipt.findByAddressAndTxid(address, ORIGINAL).orElseThrow().confirmedSats);
        assertEquals(20_000, LogicalReceipt.findByAddressAndTxid(address, ORIGINAL).orElseThrow().pendingSats);
    }

    @Test @TestTransaction
    void reorgAtualizaTodosEnderecosESaldoNaoEhSomaDeRecebimentos() {
        Address first = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        Address second = address(BitcoinTestAddresses.RECONCILIATION_SECOND);
        for (Address address : List.of(first, second)) {
            stub.addTransaction(address.canonical, info(ORIGINAL, 20_000, BitcoinTransaction.Status.CONFIRMED, INPUT));
            monitor.pollAddress(address);
            stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                    BitcoinIndexerPort.BalanceState.CONFIRMED, 7, 20_000));
        }
        monitor.handleReorg(ORIGINAL, Map.of(), Instant.now());
        for (Address address : List.of(first, second)) {
            assertEquals(BitcoinTransaction.Status.PENDING, transaction(address, ORIGINAL).status);
            assertEquals(7, AddressMonitorState.findByAddress(address).orElseThrow().confirmedBalanceSats);
        }
    }

    @Test @TestTransaction
    void falhaNaSegundaPaginaNaoAvancaCabecaNemPersisteLoteParcial() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        stub.addTransaction(address.canonical, info(ORIGINAL, 1, BitcoinTransaction.Status.CONFIRMED, INPUT));
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        for (int i = 1; i <= 30; i++) stub.addTransaction(address.canonical,
                info(String.format("%064x", i), 1, BitcoinTransaction.Status.CONFIRMED,
                        new BitcoinIndexerPort.InputInfo(String.format("%064x", i + 100), 0)));
        stub.failPage(address.canonical, String.format("%064x", 25));
        monitor.pollAddress(address);
        assertEquals(ORIGINAL, AddressMonitorState.findByAddress(address).orElseThrow().lastSeenTxid);
        assertEquals(1, BitcoinTransaction.count("address", address));
        assertFalse(AddressMonitorState.findByAddress(address).orElseThrow().providerAvailable);
    }

    @Test @TestTransaction
    void maisDeVinteCincoNovasConfirmacoesNaoSaoPerdidas() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        stub.addTransaction(address.canonical, info(ORIGINAL, 1, BitcoinTransaction.Status.CONFIRMED, INPUT));
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        for (int i = 1; i <= 51; i++) stub.addTransaction(address.canonical,
                info(String.format("%064x", i), 1, BitcoinTransaction.Status.CONFIRMED,
                        new BitcoinIndexerPort.InputInfo(String.format("%064x", i + 100), 0)));
        stub.addTransaction(address.canonical, info(ORIGINAL, 1, BitcoinTransaction.Status.CONFIRMED, INPUT));
        monitor.pollAddress(address);
        assertEquals(52, BitcoinTransaction.count("address", address));
    }

    @Test @TestTransaction
    void confirmacaoHistoricaDeSeteDiasNaoRecarregaReservaHoje() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        Pet pet = pet(address);
        Instant old = Instant.now().minusSeconds(8 * 24 * 3600);
        stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 20_000, 0));
        stub.addTransaction(address.canonical, new BitcoinIndexerPort.TransactionInfo(
                ORIGINAL, 20_000, BitcoinTransaction.Status.CONFIRMED, Instant.now(), old, 100,
                "aa".repeat(32), List.of(new BitcoinIndexerPort.OutputInfo(0, address.canonical, 20_000)), List.of(INPUT)));
        monitor.pollAddress(address);
        PetFeeding feeding = PetFeeding.listByPet(pet).getFirst();
        assertEquals(0, pet.reserveHours.signum(), "histórico vencido não pode virar 24h atuais");
        assertEquals(FeedingOrigin.HISTORICAL_RECONSTRUCTION, feeding.origin);
        assertFalse(feeding.presentable);
        assertEquals(old, feeding.effectiveAt);
    }

    @Test @TestTransaction
    void falhaNaConsultaIndividualPreservaUltimoSaldoConfirmado() {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 20_000, 0));
        stub.addTransaction(address.canonical, info(ORIGINAL, 20_000, BitcoinTransaction.Status.CONFIRMED, INPUT));
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        stub.setTransactionLookup(address.canonical, ORIGINAL,
                new BitcoinIndexerPort.TransactionLookup(BitcoinIndexerPort.LookupState.UNAVAILABLE, null));
        monitor.pollAddress(address);
        assertEquals(20_000, AddressMonitorState.findByAddress(address).orElseThrow().confirmedBalanceSats);
        assertFalse(AddressMonitorState.findByAddress(address).orElseThrow().providerAvailable);
        assertEquals(BitcoinTransaction.Status.CONFIRMED, transaction(address, ORIGINAL).status);
    }

    @Test @TestTransaction
    void reconfirmacaoEmOutroBlocoAtualizaHashEEmiteReorgComSaldoReal() throws Exception {
        Address address = address(BitcoinTestAddresses.RECONCILIATION_FIRST);
        stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 7, 0));
        stub.addTransaction(address.canonical, info(ORIGINAL, 20_000, BitcoinTransaction.Status.CONFIRMED, INPUT));
        monitor.pollAddress(address);
        stub.resetAddress(address.canonical);
        stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED, 5, 0));
        var updated = new BitcoinIndexerPort.TransactionInfo(ORIGINAL, 20_000,
                BitcoinTransaction.Status.CONFIRMED, Instant.now(), Instant.now(), 101, "ff".repeat(32),
                List.of(new BitcoinIndexerPort.OutputInfo(0, address.canonical, 20_000)), List.of(INPUT));
        stub.addTransaction(address.canonical, updated);
        monitor.pollAddress(address);
        assertEquals("ff".repeat(32), transaction(address, ORIGINAL).blockHash);
        br.com.satoshipet.api.outbox.OutboxEvent event = br.com.satoshipet.api.outbox.OutboxEvent.find(
                "aggregateId = ?1 and eventType = ?2", address.canonical, "BITCOIN_CHAIN_REORG").firstResult();
        assertNotNull(event);
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.payload).path("payload");
        assertEquals(7, payload.path("previousConfirmedBalanceSats").asLong());
        assertEquals(5, payload.path("currentConfirmedBalanceSats").asLong());
        assertTrue(payload.path("oldTip").isNull());
    }

    private Address address(String canonical) {
        assertTrue(Address.findByCanonical(canonical).isEmpty(), "fixture deve ter endereço isolado");
        Address address = Address.create(canonical, Instant.now()); address.persist(); return address;
    }
    private Pet pet(Address address) {
        Instant now = Instant.now();
        Account account = Account.create("reconcile-" + UUID.randomUUID() + "@test.com", "Etc/UTC", "pt-BR", now);
        account.persist();
        br.com.satoshipet.api.account.AccountAddressBinding.create(account, address, true, now).persist();
        Pet pet = Pet.create(address, account, "Reconciliação", now); pet.presentation = PetPresentation.CREATURE; pet.persist();
        portions.recordPositivePortion(pet.id, account.id, 20_000, PortionOrigin.CREATOR_PLAN, now);
        return pet;
    }
    private void pending(Address address, String txid, long sats, BitcoinIndexerPort.InputInfo input) {
        stub.addMempoolTransaction(address.canonical, info(txid, sats, BitcoinTransaction.Status.PENDING, input));
    }
    private BitcoinTransaction transaction(Address address, String txid) {
        return BitcoinTransaction.find("address = ?1 and txid = ?2", address, txid).firstResult();
    }
    private BitcoinIndexerPort.TransactionInfo info(String txid, long sats, BitcoinTransaction.Status status,
            BitcoinIndexerPort.InputInfo input) {
        boolean confirmed = status == BitcoinTransaction.Status.CONFIRMED;
        return new BitcoinIndexerPort.TransactionInfo(txid, sats, status, Instant.now(),
                confirmed ? Instant.now() : null, confirmed ? 100 : null, confirmed ? "aa".repeat(32) : null,
                sats == 0 ? List.of() : List.of(new BitcoinIndexerPort.OutputInfo(0, "address", sats)), List.of(input));
    }
}
