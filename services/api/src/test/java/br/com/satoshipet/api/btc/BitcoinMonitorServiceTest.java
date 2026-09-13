package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.FeedingStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetFeeding;
import br.com.satoshipet.api.pet.PetPresentation;
import br.com.satoshipet.api.pet.PetReferencePortion;
import br.com.satoshipet.api.pet.PetReferencePortionPort;
import br.com.satoshipet.api.pet.PortionOrigin;
import br.com.satoshipet.api.support.bitcoin.BitcoinRbfFixture;
import br.com.satoshipet.api.support.bitcoin.BitcoinReorgFixture;
import br.com.satoshipet.api.support.bitcoin.BitcoinTransactionFixture;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração para {@link BitcoinMonitorService}.
 *
 * <p>Usa H2 em modo PostgreSQL e o {@link StubBitcoinIndexer} selecionado
 * pelo perfil de teste. Cada método de teste roda em sua própria transação
 * (via {@link Transactional}) que é commitada ao final, garantindo que
 * as queries dentro do método vejam dados criados no mesmo teste.</p>
 */
@QuarkusTest
class BitcoinMonitorServiceTest {

    private static final String FIXTURE_ADDR = BitcoinTransactionFixture.ADDRESS.toLowerCase();
    private static final String RBF_ADDR     = BitcoinRbfFixture.ADDRESS.toLowerCase();
    private static final String REORG_ADDR   = BitcoinReorgFixture.ADDRESS.toLowerCase();

    @Inject BitcoinMonitorService monitorService;
    @Inject StubBitcoinIndexer stub;
    @Inject PetReferencePortionPort portionPort;

    private static final BigDecimal TWENTY_FOUR_HOURS =
            new BigDecimal("24").setScale(10);

    @BeforeEach
    @Transactional
    void limpar() {
        stub.reset();
        deletarEnderecoDeFixture(FIXTURE_ADDR);
        deletarEnderecoDeFixture(RBF_ADDR);
        deletarEnderecoDeFixture(REORG_ADDR);
    }

    private void deletarEnderecoDeFixture(String canonical) {
        Address.findByCanonical(canonical).ifPresent(address -> {
            List<Account> contas = new ArrayList<>();
            Pet.findByAddress(address).ifPresent(pet -> {
                PetFeeding.delete("pet", pet);
                PetReferencePortion.delete("pet", pet);
                contas.add(pet.creatorAccount);
                pet.delete();
            });
            AccountAddressBinding.find("address", address).<AccountAddressBinding>list()
                    .forEach(binding -> {
                        contas.add(binding.account);
                        binding.delete();
                    });
            LogicalReceipt.find("address", address).list()
                    .forEach(r -> ((LogicalReceipt) r).delete());
            BitcoinTransaction.find("address", address).list()
                    .forEach(t -> ((BitcoinTransaction) t).delete());
            // address_monitor_state tem ON DELETE CASCADE — deletado automaticamente
            address.delete();
            contas.stream()
                    .map(conta -> conta.id)
                    .distinct()
                    .forEach(id -> {
                        Account conta = Account.findById(id);
                        if (conta != null) {
                            conta.delete();
                        }
                    });
        });
    }

    // -------------------------------------------------------------------------
    // Idempotência de persistência
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void pollDuasVezesComMesmaTxPersisteSomenteUmaTransacao() {
        Address address = criarEndereco(FIXTURE_ADDR);
        configureStubConfirmed(address.canonical);

        monitorService.pollAddress(address);
        monitorService.pollAddress(address);

        List<BitcoinTransaction> txs = BitcoinTransaction.list("address", address);
        assertEquals(1, txs.size(), "Deve persistir exatamente 1 transação mesmo com 2 polls");
    }

    @Test
    @Transactional
    void pollDuasVezesComMesmaTxPersisteSomenteUmLogicalReceipt() {
        Address address = criarEndereco(FIXTURE_ADDR);
        configureStubConfirmed(address.canonical);

        monitorService.pollAddress(address);
        monitorService.pollAddress(address);

        List<LogicalReceipt> receipts = LogicalReceipt.findByAddress(address);
        assertEquals(1, receipts.size(), "Deve persistir exatamente 1 LogicalReceipt");
    }

    // -------------------------------------------------------------------------
    // Transição PENDING → CONFIRMED
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void transacaoPendenteFicaConfirmadaNaSegundaRodada() {
        Address address = criarEndereco(FIXTURE_ADDR);

        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.PENDING,
                BitcoinTransactionFixture.MEMPOOL_AT
        ));
        monitorService.pollAddress(address);

        BitcoinTransaction tx = BitcoinTransaction.find("address = ?1 AND txid = ?2",
                address, BitcoinTransactionFixture.TXID).firstResult();
        assertNotNull(tx, "Transação deve ser persistida");
        assertEquals(BitcoinTransaction.Status.PENDING, tx.status);

        // Segundo poll: mesma tx agora confirmada
        stub.resetAddress(address.canonical);
        stub.addTransaction(address.canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinTransactionFixture.CONFIRMED_AT
        ));
        monitorService.pollAddress(address);

        BitcoinTransaction txUpdated = BitcoinTransaction.find("address = ?1 AND txid = ?2",
                address, BitcoinTransactionFixture.TXID).firstResult();
        assertNotNull(txUpdated);
        assertEquals(BitcoinTransaction.Status.CONFIRMED, txUpdated.status,
                "Status deve ser CONFIRMED após segundo poll");
    }

    @Test
    @Transactional
    void logicalReceiptAtualizaConfirmedSatsAposConfirmacao() {
        Address address = criarEndereco(FIXTURE_ADDR);

        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.PENDING,
                BitcoinTransactionFixture.MEMPOOL_AT
        ));
        monitorService.pollAddress(address);

        stub.resetAddress(address.canonical);
        stub.addTransaction(address.canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinTransactionFixture.CONFIRMED_AT
        ));
        monitorService.pollAddress(address);

        List<LogicalReceipt> receipts = LogicalReceipt.findByAddress(address);
        assertEquals(1, receipts.size());
        assertEquals(BitcoinTransactionFixture.AMOUNT_SATS, receipts.get(0).confirmedSats,
                "confirmedSats deve ser atualizado após confirmação");
        assertEquals(0L, receipts.get(0).pendingSats,
                "pendingSats deve ser zerado após confirmação");
    }

    // -------------------------------------------------------------------------
    // RBF (Replace-By-Fee)
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void rbfOriginalFicaReplacedEReplacementEhCriado() {
        Address address = criarEndereco(RBF_ADDR);

        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinRbfFixture.ORIGINAL_TXID,
                BitcoinRbfFixture.ORIGINAL_RECEIVED_SATS,
                BitcoinTransaction.Status.PENDING,
                BitcoinRbfFixture.ORIGINAL_MEMPOOL_AT
        ));
        monitorService.pollAddress(address);

        stub.resetAddress(address.canonical);
        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinRbfFixture.ORIGINAL_TXID,
                BitcoinRbfFixture.ORIGINAL_RECEIVED_SATS,
                BitcoinTransaction.Status.REPLACED,
                BitcoinRbfFixture.ORIGINAL_REPLACED_AT
        ));
        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinRbfFixture.REPLACEMENT_TXID,
                BitcoinRbfFixture.REPLACEMENT_RECEIVED_SATS,
                BitcoinTransaction.Status.PENDING,
                BitcoinRbfFixture.REPLACEMENT_AT
        ));
        monitorService.pollAddress(address);

        BitcoinTransaction original = BitcoinTransaction.find("address = ?1 AND txid = ?2",
                address, BitcoinRbfFixture.ORIGINAL_TXID).firstResult();
        assertEquals(BitcoinTransaction.Status.REPLACED, original.status,
                "Original deve ficar REPLACED");

        BitcoinTransaction replacement = BitcoinTransaction.find("address = ?1 AND txid = ?2",
                address, BitcoinRbfFixture.REPLACEMENT_TXID).firstResult();
        assertNotNull(replacement, "Replacement deve ser criado");
        assertEquals(BitcoinTransaction.Status.PENDING, replacement.status);
    }

    @Test
    @Transactional
    void rbfOriginalZeraPendingSatsDoLogicalReceipt() {
        Address address = criarEndereco(RBF_ADDR);

        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinRbfFixture.ORIGINAL_TXID,
                BitcoinRbfFixture.ORIGINAL_RECEIVED_SATS,
                BitcoinTransaction.Status.PENDING,
                BitcoinRbfFixture.ORIGINAL_MEMPOOL_AT
        ));
        monitorService.pollAddress(address);

        stub.resetAddress(address.canonical);
        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinRbfFixture.ORIGINAL_TXID,
                BitcoinRbfFixture.ORIGINAL_RECEIVED_SATS,
                BitcoinTransaction.Status.REPLACED,
                BitcoinRbfFixture.ORIGINAL_REPLACED_AT
        ));
        monitorService.pollAddress(address);

        LogicalReceipt receipt = LogicalReceipt.findByAddressAndTxid(address, BitcoinRbfFixture.ORIGINAL_TXID)
                .orElseThrow(() -> new AssertionError("LogicalReceipt não encontrado"));
        assertEquals(0L, receipt.pendingSats,
                "pendingSats deve ser zerado quando original é REPLACED");
    }

    // -------------------------------------------------------------------------
    // Reorg
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void reorgRetornaTransacaoParaPending() {
        Address address = criarEndereco(REORG_ADDR);

        stub.addTransaction(address.canonical, txInfo(
                BitcoinReorgFixture.TXID,
                BitcoinReorgFixture.RECEIVED_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinReorgFixture.ORIGINAL_CONFIRMATION_AT
        ));
        monitorService.pollAddress(address);

        BitcoinTransaction tx = BitcoinTransaction.find("address = ?1 AND txid = ?2",
                address, BitcoinReorgFixture.TXID).firstResult();
        assertEquals(BitcoinTransaction.Status.CONFIRMED, tx.status);

        monitorService.handleReorg(
                BitcoinReorgFixture.TXID,
                Map.of(
                        "oldTip", Map.of("hash", BitcoinReorgFixture.OLD_TIP_HASH,
                                "height", BitcoinReorgFixture.OLD_TIP_HEIGHT),
                        "newTip", Map.of("hash", BitcoinReorgFixture.NEW_TIP_HASH,
                                "height", BitcoinReorgFixture.NEW_TIP_HEIGHT),
                        "forkHeight", BitcoinReorgFixture.OLD_BLOCK_HEIGHT,
                        "depth", 1
                ),
                BitcoinReorgFixture.REORG_AT
        );

        BitcoinTransaction txAfterReorg = BitcoinTransaction.find("address = ?1 AND txid = ?2",
                address, BitcoinReorgFixture.TXID).firstResult();
        assertEquals(BitcoinTransaction.Status.PENDING, txAfterReorg.status,
                "Transação deve voltar a PENDING após reorg");
    }

    @Test
    @Transactional
    void reorgRecalculaSaldosLogicalReceipt() {
        Address address = criarEndereco(REORG_ADDR);

        stub.addTransaction(address.canonical, txInfo(
                BitcoinReorgFixture.TXID,
                BitcoinReorgFixture.RECEIVED_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinReorgFixture.ORIGINAL_CONFIRMATION_AT
        ));
        monitorService.pollAddress(address);

        monitorService.handleReorg(
                BitcoinReorgFixture.TXID,
                Map.of("oldTip", Map.of(), "newTip", Map.of(), "forkHeight", 0, "depth", 1),
                BitcoinReorgFixture.REORG_AT
        );

        List<LogicalReceipt> receipts = LogicalReceipt.findByAddress(address);
        assertFalse(receipts.isEmpty());
        LogicalReceipt receipt = receipts.get(0);

        assertEquals(0L, receipt.confirmedSats, "confirmedSats deve ser zerado após reorg");
        assertTrue(receipt.pendingSats > 0, "pendingSats deve ser restaurado após reorg");
    }

    // -------------------------------------------------------------------------
    // PROVIDER_FAILURE não altera estado local (CA-031)
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void providerFailureNaoAlteraEstadoLocal() {
        Address address = criarEndereco(FIXTURE_ADDR);
        configureStubConfirmed(address.canonical);
        monitorService.pollAddress(address);

        stub.resetAddress(address.canonical);
        stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE, 0L, 0L
        ));
        monitorService.pollAddress(address);

        List<BitcoinTransaction> txs = BitcoinTransaction.list("address", address);
        assertEquals(1, txs.size(), "Transação deve permanecer após PROVIDER_FAILURE (CA-031)");
    }

    // -------------------------------------------------------------------------
    // Notificação pet (sem pet cadastrado → sem erro)
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void pollSemPetAssociadoNaoLancaExcecao() {
        Address address = criarEndereco(FIXTURE_ADDR);
        configureStubConfirmed(address.canonical);

        // Não deve lançar exceção mesmo sem Pet associado ao endereço
        monitorService.pollAddress(address);

        // Verifica que a transação foi persistida mesmo assim
        List<BitcoinTransaction> txs = BitcoinTransaction.list("address", address);
        assertEquals(1, txs.size());
        assertTrue(Pet.findByAddress(address).isEmpty());
    }

    @Test
    @Transactional
    void pollConfirmadoComPetCriaAlimentacaoValidaDeVinteQuatroHoras() {
        Address address = criarEndereco(FIXTURE_ADDR);
        Pet pet = criarPetComPorcao(address, PetPresentation.EGG);
        configureStubConfirmed(address.canonical);

        monitorService.pollAddress(address);

        List<PetFeeding> feedings = PetFeeding.listByPet(pet);
        assertEquals(1, feedings.size(), "Deve criar exatamente 1 alimentação");
        PetFeeding feeding = feedings.get(0);
        assertEquals(FeedingStatus.VALID, feeding.status);
        assertEquals(BitcoinTransactionFixture.AMOUNT_SATS, feeding.amountSats);
        assertEquals(0, feeding.durationHours.compareTo(TWENTY_FOUR_HOURS));
        Pet stored = Pet.findById(pet.id);
        assertEquals(0, stored.reserveHours.compareTo(TWENTY_FOUR_HOURS));
    }

    @Test
    @Transactional
    void pollDuasVezesComPetPersisteSomenteUmaAlimentacao() {
        Address address = criarEndereco(FIXTURE_ADDR);
        Pet pet = criarPetComPorcao(address, PetPresentation.EGG);
        configureStubConfirmed(address.canonical);

        monitorService.pollAddress(address);
        monitorService.pollAddress(address);

        assertEquals(1, PetFeeding.listByPet(pet).size(),
                "Segundo poll não duplica alimentação (CA-017)");
        Pet stored = Pet.findById(pet.id);
        assertTrue(stored.reserveHours.compareTo(TWENTY_FOUR_HOURS) <= 0,
                "segundo poll não pode somar outra porção");
        assertTrue(stored.reserveHours.compareTo(new BigDecimal("23").setScale(10)) > 0,
                "onBalanceKnown só consome o relógio entre polls");
    }

    @Test
    @Transactional
    void providerFailureComPetNaoAlteraReservaNemApagaTx() {
        Address address = criarEndereco(FIXTURE_ADDR);
        Pet pet = criarPetComPorcao(address, PetPresentation.EGG);
        configureStubConfirmed(address.canonical);
        monitorService.pollAddress(address);

        Pet afterFeed = Pet.findById(pet.id);
        assertEquals(0, afterFeed.reserveHours.compareTo(TWENTY_FOUR_HOURS));

        stub.resetAddress(address.canonical);
        stub.setBalance(address.canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE, 0L, 0L
        ));
        monitorService.pollAddress(address);

        Pet afterFailure = Pet.findById(pet.id);
        assertEquals(0, afterFailure.reserveHours.compareTo(TWENTY_FOUR_HOURS),
                "PROVIDER_FAILURE não altera reserva (CA-031)");
        List<BitcoinTransaction> txs = BitcoinTransaction.list("address", address);
        assertEquals(1, txs.size(), "Transação deve permanecer após PROVIDER_FAILURE");
        assertEquals(1, PetFeeding.listByPet(pet).size());
    }

    @Test
    @Transactional
    void mempoolDepoisConfirmadoNaCriaturaNaoDobraHoras() {
        Address address = criarEndereco(FIXTURE_ADDR);
        Pet pet = criarPetComPorcao(address, PetPresentation.CREATURE);

        stub.addMempoolTransaction(address.canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.PENDING,
                BitcoinTransactionFixture.MEMPOOL_AT
        ));
        monitorService.pollAddress(address);

        Pet afterPending = Pet.findById(pet.id);
        assertEquals(1, PetFeeding.listByPet(pet).size());
        assertEquals(FeedingStatus.PROVISIONAL, PetFeeding.listByPet(pet).get(0).status);
        assertEquals(0, afterPending.reserveHours.compareTo(TWENTY_FOUR_HOURS));

        stub.resetAddress(address.canonical);
        stub.addTransaction(address.canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinTransactionFixture.CONFIRMED_AT
        ));
        monitorService.pollAddress(address);

        Pet afterConfirm = Pet.findById(pet.id);
        List<PetFeeding> feedings = PetFeeding.listByPet(pet);
        assertEquals(1, feedings.size(), "Confirmação não cria segunda alimentação (CA-028)");
        assertEquals(FeedingStatus.VALID, feedings.get(0).status);
        assertEquals(0, afterConfirm.reserveHours.compareTo(TWENTY_FOUR_HOURS),
                "Confirmação da criatura não dobra as horas");
    }

    // -------------------------------------------------------------------------
    // Helpers (sem @Transactional — herdam transação do método de teste)
    // -------------------------------------------------------------------------

    private Address criarEndereco(String canonical) {
        return Address.findByCanonical(canonical).orElseGet(() -> {
            Address a = Address.create(canonical, Instant.now());
            a.persist();
            return a;
        });
    }

    private Pet criarPetComPorcao(Address address, PetPresentation presentation) {
        Instant now = Instant.now();
        Account account = Account.create(
                "monitor-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                now
        );
        account.persist();
        AccountAddressBinding.create(account, address, true, now).persist();
        Pet pet = Pet.create(address, account, "Pixel-monitor", now);
        pet.presentation = presentation;
        pet.persist();
        portionPort.recordPositivePortion(
                pet.id,
                account.id,
                BitcoinTransactionFixture.AMOUNT_SATS,
                PortionOrigin.CREATOR_PLAN,
                now
        );
        return pet;
    }

    private void configureStubConfirmed(String canonical) {
        stub.setBalance(canonical, new BitcoinIndexerPort.BalanceResult(
                BitcoinIndexerPort.BalanceState.CONFIRMED,
                BitcoinTransactionFixture.AMOUNT_SATS, 0L
        ));
        stub.addTransaction(canonical, txInfo(
                BitcoinTransactionFixture.TXID,
                BitcoinTransactionFixture.AMOUNT_SATS,
                BitcoinTransaction.Status.CONFIRMED,
                BitcoinTransactionFixture.CONFIRMED_AT
        ));
    }

    private BitcoinIndexerPort.TransactionInfo txInfo(
            String txid, long amountSats, BitcoinTransaction.Status status, Instant at
    ) {
        boolean confirmed = status == BitcoinTransaction.Status.CONFIRMED;
        return new BitcoinIndexerPort.TransactionInfo(
                txid, amountSats, status,
                at,
                confirmed ? at : null,
                confirmed ? BitcoinTransactionFixture.BLOCK_HEIGHT : null,
                confirmed ? BitcoinTransactionFixture.BLOCK_HASH : null,
                List.of(new BitcoinIndexerPort.OutputInfo(0, "bcrt1qtest", amountSats))
        );
    }
}
