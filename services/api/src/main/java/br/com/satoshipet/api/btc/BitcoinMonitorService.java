package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.events.DomainEventType;
import br.com.satoshipet.api.outbox.OutboxService;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetLifecyclePort;
import br.com.satoshipet.api.platform.CorrelationIdContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Serviço central do monitor Bitcoin.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Consultar o indexador ({@link BitcoinIndexerPort}) por endereço.</li>
 *   <li>Persistir {@link BitcoinTransaction}, {@link BitcoinOutput} e
 *       {@link LogicalReceipt} de forma idempotente.</li>
 *   <li>Gerenciar transições de estado: MEMPOOL → CONFIRMED, REPLACED, DROPPED.</li>
 *   <li>Notificar o ciclo de vida do pet via {@link PetLifecyclePort}.</li>
 *   <li>Emitir eventos de domínio redagidos via {@link OutboxService}.</li>
 * </ul></p>
 *
 * <p>Invariantes críticas preservadas:
 * <ul>
 *   <li>Somente recebimentos on-chain reais alimentam o pet (CA-028, PRD §5).</li>
 *   <li>Eventos de compra declarada NUNCA disparam {@code PetLifecyclePort}.</li>
 *   <li>Cálculos em sats usam {@code long}; nunca ponto flutuante (CC-10).</li>
 *   <li>Falha de provedor ≠ saldo zero (CA-031).</li>
 * </ul></p>
 */
@ApplicationScoped
public class BitcoinMonitorService {

    private static final Logger LOG = Logger.getLogger(BitcoinMonitorService.class);

    /** Tamanho da página de transações solicitada ao indexador por ciclo. */
    static final int TX_PAGE_SIZE = 25;

    /** Número máximo de endereços ativos retornados por ciclo. */
    static final int MAX_ACTIVE_ADDRESSES = 500;

    private final BitcoinIndexerPort indexer;
    private final OutboxService outboxService;
    private final PetLifecyclePort petLifecycle;
    private final BitcoinEventRedactor redactor;

    @Inject
    public BitcoinMonitorService(
            BitcoinIndexerPort indexer,
            OutboxService outboxService,
            PetLifecyclePort petLifecycle,
            BitcoinEventRedactor redactor
    ) {
        this.indexer      = indexer;
        this.outboxService = outboxService;
        this.petLifecycle  = petLifecycle;
        this.redactor      = redactor;
    }

    // -------------------------------------------------------------------------
    // Consulta de endereços ativos (usada pelo job)
    // -------------------------------------------------------------------------

    /**
     * Retorna endereços ativos que devem ser monitorados.
     *
     * <p>Considera todos os {@link Address} que possuem um
     * {@link br.com.satoshipet.api.account.AccountAddressBinding} ou
     * um {@link Pet} associado. Atualmente simplificado para listar todos
     * os {@link Address} existentes (refinado com épicos CONTA e PET).</p>
     */
    public List<Address> getActiveAddresses() {
        return Address.listAll();
    }

    // -------------------------------------------------------------------------
    // Polling por endereço (chamado pelo job)
    // -------------------------------------------------------------------------

    /**
     * Realiza uma rodada de polling para o endereço informado.
     *
     * <p>Cada invocação roda em sua própria transação (REQUIRED). Falhas em um
     * endereço não afetam os demais.</p>
     *
     * @param address endereço Bitcoin a monitorar
     */
    @Transactional
    public void pollAddress(Address address) {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open(CorrelationIdContext.current())) {
            pollAddressWithContext(address);
        }
    }

    private void pollAddressWithContext(Address address) {
        lockAddress(address);
        Instant now = Instant.now();
        AddressMonitorState state = getOrCreateState(address, now);
        BitcoinIndexerPort.BalanceResult balance = indexer.getBalance(address.canonical);
        if (balance.state() == BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE) {
            providerFailure(address, state, now);
            return;
        }
        var mempool = indexer.getMempoolPage(address.canonical);
        ConfirmedScan scan = scanConfirmed(address, state);
        if (!mempool.available() || scan == null) {
            providerFailure(address, state, now);
            return;
        }
        // Consultas primeiro: falha de uma página/evidência não publica lote parcial.
        var incoming = new java.util.LinkedHashMap<String, BitcoinIndexerPort.TransactionInfo>();
        mempool.transactions().forEach(tx -> incoming.put(tx.txid(), tx));
        scan.transactions().forEach(tx -> incoming.put(tx.txid(), tx));
        if (!collectEvidence(address, incoming)) {
            providerFailure(address, state, now);
            return;
        }
        Long previousBalance = state.confirmedBalanceSats;
        state.recordBalance(balance.confirmedSats(), balance.pendingSats(), now);
        // Materializa inputs de registros anteriores antes de procurar conflitos.
        for (var info : incoming.values()) {
            findTransaction(address, info.txid()).ifPresent(tx -> BitcoinInput.record(tx, info.inputs()));
        }
        for (var info : incoming.values()) processTransaction(address, info, previousBalance, now);
        state.lastSeenTxid = scan.head();
        state.cursor = scan.cursor();
        state.backfillComplete = scan.backfillComplete();
        state.lastCheckedAt = now;
        publishBalance(address, state, balance, now);
    }

    private record ConfirmedScan(List<BitcoinIndexerPort.TransactionInfo> transactions,
                                 String head, String cursor, boolean backfillComplete) {}

    private ConfirmedScan scanConfirmed(Address address, AddressMonitorState state) {
        String previousHead = state.lastSeenTxid;
        String cursor = null;
        String newHead = previousHead;
        String backfillCursor = state.cursor;
        boolean complete = state.backfillComplete;
        var gathered = new ArrayList<BitcoinIndexerPort.TransactionInfo>();
        var seenPages = new java.util.HashSet<String>();
        while (true) {
            var page = indexer.getTransactionPage(address.canonical, cursor, TX_PAGE_SIZE);
            if (!page.available()) return null;
            var transactions = page.transactions();
            gathered.addAll(transactions);
            if (cursor == null && !transactions.isEmpty()) newHead = transactions.getFirst().txid();
            boolean foundHead = previousHead != null && transactions.stream().anyMatch(tx -> tx.txid().equals(previousHead));
            if (previousHead == null) {
                if (!transactions.isEmpty()) backfillCursor = transactions.getLast().txid();
                complete = transactions.size() < TX_PAGE_SIZE;
                break;
            }
            if (foundHead || transactions.size() < TX_PAGE_SIZE) break;
            cursor = transactions.getLast().txid();
            if (!seenPages.add(cursor)) return null;
        }
        if (previousHead != null && !complete) {
            var page = indexer.getTransactionPage(address.canonical, backfillCursor, TX_PAGE_SIZE);
            if (!page.available()) return null;
            gathered.addAll(page.transactions());
            if (!page.transactions().isEmpty()) backfillCursor = page.transactions().getLast().txid();
            complete = page.transactions().size() < TX_PAGE_SIZE;
        }
        return new ConfirmedScan(gathered, newHead, backfillCursor, complete);
    }

    /** Reconcilia também transações antigas, ausentes da página mais recente. */
    private boolean collectEvidence(Address address, Map<String, BitcoinIndexerPort.TransactionInfo> incoming) {
        List<BitcoinTransaction> active = BitcoinTransaction.list("address = ?1 and status in (?2, ?3)",
                address, BitcoinTransaction.Status.PENDING, BitcoinTransaction.Status.CONFIRMED);
        for (BitcoinTransaction tx : active) {
            if (incoming.containsKey(tx.txid)) continue;
            var lookup = indexer.getTransaction(address.canonical, tx.txid);
            if (lookup.state() == BitcoinIndexerPort.LookupState.UNAVAILABLE) return false;
            if (lookup.state() == BitcoinIndexerPort.LookupState.FOUND) {
                incoming.put(tx.txid, lookup.transaction());
                continue;
            }
            // 404 não prova descarte. Só o gasto de um input por outra transação prova conflito.
            for (var input : BitcoinInput.inputsOf(tx)) {
                var spend = indexer.getOutspend(input);
                if (!spend.available()) return false;
                if (spend.spendingTxid() == null || spend.spendingTxid().equals(tx.txid)) continue;
                var replacement = incoming.get(spend.spendingTxid());
                if (replacement == null) {
                    var replacementLookup = indexer.getTransaction(address.canonical, spend.spendingTxid());
                    if (replacementLookup.state() != BitcoinIndexerPort.LookupState.FOUND) return false;
                    replacement = replacementLookup.transaction();
                }
                if (!replacement.inputs().contains(input)) return false;
                incoming.put(replacement.txid(), replacement);
            }
        }
        // Se duas respostas concorrentes contradizem os inputs, exige outspend atual.
        var spenderByInput = new java.util.HashMap<BitcoinIndexerPort.InputInfo, String>();
        var superseded = new java.util.HashSet<String>();
        for (var info : incoming.values()) {
            if (!isActive(info.status())) continue;
            for (var input : info.inputs()) {
                String other = spenderByInput.putIfAbsent(input, info.txid());
                if (other == null || other.equals(info.txid())) continue;
                var spend = indexer.getOutspend(input);
                if (!spend.available() || spend.spendingTxid() == null
                        || (!spend.spendingTxid().equals(other) && !spend.spendingTxid().equals(info.txid()))) return false;
                superseded.add(spend.spendingTxid().equals(other) ? info.txid() : other);
                spenderByInput.put(input, spend.spendingTxid());
            }
        }
        superseded.forEach(incoming::remove);
        return true;
    }

    private void processTransaction(Address address, BitcoinIndexerPort.TransactionInfo info,
                                    Long previousBalance, Instant now) {
        Optional<BitcoinTransaction> found = findTransaction(address, info.txid());
        if (found.isEmpty()) {
            createTransaction(address, info, now);
            return;
        }
        BitcoinTransaction tx = found.get();
        if (tx.logicalReceipt == null) tx.logicalReceipt = LogicalReceipt.findByAddressAndTxid(address, tx.txid).orElse(null);
        BitcoinInput.record(tx, info.inputs());
        BitcoinTransaction.Status previousStatus = tx.status;
        Map<String, Object> previousBlock = buildBlockMap(tx);
        boolean changed = previousStatus != info.status() || tx.amountSats != info.amountSats()
                || !java.util.Objects.equals(tx.blockHash, info.blockHash());
        if (!changed) return;
        boolean reorg = previousStatus == BitcoinTransaction.Status.CONFIRMED
                && (info.status() == BitcoinTransaction.Status.PENDING
                    || (info.status() == BitcoinTransaction.Status.CONFIRMED
                        && !java.util.Objects.equals(tx.blockHash, info.blockHash())));
        applyTransactionState(tx, info, now);
        updateReceipt(tx, now);
        if (reorg) emitChainReorgEvent(address, tx, reorgData(previousBlock, previousBalance), now);
        else if (info.status() == BitcoinTransaction.Status.CONFIRMED) {
            emitTransactionConfirmedEvent(address, tx, previousStatus, info, now);
        } else if (info.status() == BitcoinTransaction.Status.REPLACED) {
            emitTransactionReplacedEvent(address, tx, info, now);
        } else if (info.status() == BitcoinTransaction.Status.DROPPED) {
            emitTransactionDroppedEvent(address, tx, previousStatus, info, now);
        }
    }

    private void createTransaction(Address address, BitcoinIndexerPort.TransactionInfo info, Instant now) {
        var conflicts = BitcoinInput.conflicts(address, info.inputs());
        BitcoinTransaction tx = BitcoinTransaction.createPending(info.txid(), address, info.amountSats(),
                info.observedAt() != null ? info.observedAt() : now);
        applyTransactionState(tx, info, now);
        LogicalReceipt receipt = conflicts.stream().map(old -> old.logicalReceipt)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (receipt == null && info.amountSats() > 0) {
            receipt = LogicalReceipt.findByAddressAndTxid(address, info.txid()).orElse(null);
            if (receipt == null) {
                receipt = LogicalReceipt.createPending(address, info.txid(), info.amountSats(), now);
                receipt.persist();
            }
        }
        tx.logicalReceipt = receipt;
        tx.persist();
        BitcoinInput.record(tx, info.inputs());
        for (var output : info.outputs()) {
            BitcoinOutput.create(tx, output.vout(), address, output.amountSats(), null).persist();
        }
        for (BitcoinTransaction original : conflicts) {
            original.status = BitcoinTransaction.Status.REPLACED;
            // Uma substituta pode reunir duas transações conflitantes: conserva uma
            // identidade e invalida as demais, sem creditar o valor mais de uma vez.
            if (original.logicalReceipt != null && original.logicalReceipt != receipt) invalidateReceipt(original.logicalReceipt, now);
            emitTransactionReplacedEvent(address, original, info, now);
        }
        updateReceipt(tx, now);
        if (info.amountSats() > 0) emitTransactionObservedEvent(address, tx, info, now);
    }

    private static boolean isActive(BitcoinTransaction.Status status) {
        return status == BitcoinTransaction.Status.PENDING || status == BitcoinTransaction.Status.CONFIRMED;
    }

    private void applyTransactionState(BitcoinTransaction tx, BitcoinIndexerPort.TransactionInfo info, Instant now) {
        tx.status = info.status(); tx.amountSats = info.amountSats();
        boolean confirmed = info.status() == BitcoinTransaction.Status.CONFIRMED;
        tx.confirmedAt = confirmed ? (info.confirmedAt() == null ? now : info.confirmedAt()) : null;
        tx.blockHeight = confirmed ? info.blockHeight() : null;
        tx.blockHash = confirmed ? info.blockHash() : null;
    }

    private void updateReceipt(BitcoinTransaction tx, Instant now) {
        LogicalReceipt receipt = tx.logicalReceipt;
        if (receipt == null) return; // Saída sem recebimento não alimenta.
        if (!isActive(tx.status) || tx.amountSats == 0) {
            invalidateReceipt(receipt, now);
            return;
        }
        receipt.amountSats = tx.amountSats;
        receipt.confirmedSats = tx.status == BitcoinTransaction.Status.CONFIRMED ? tx.amountSats : 0;
        receipt.pendingSats = tx.status == BitcoinTransaction.Status.PENDING ? tx.amountSats : 0;
        receipt.updatedAt = now.isBefore(receipt.createdAt) ? receipt.createdAt : now;
        notifyPet(tx.address, pet -> petLifecycle.onReceiptObserved(pet.id, receipt.id, tx.amountSats,
                tx.status == BitcoinTransaction.Status.CONFIRMED, now));
    }

    private void invalidateReceipt(LogicalReceipt receipt, Instant now) {
        receipt.amountSats = 0; receipt.confirmedSats = 0; receipt.pendingSats = 0;
        receipt.updatedAt = now.isBefore(receipt.createdAt) ? receipt.createdAt : now;
        notifyPet(receipt.address, pet -> petLifecycle.onReceiptInvalidated(pet.id, receipt.id, now));
    }

    /** Reorg explícito é aplicado a todas as observações do txid, sem somar recebimentos como saldo. */
    @Transactional
    public void handleReorg(String txid, Map<String, Object> reorgEvent, Instant now) {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open(CorrelationIdContext.current())) {
            List<BitcoinTransaction> affected = BitcoinTransaction.list("txid = ?1 order by address.id", txid);
            // Ordem determinística e comum aos demais escritores: pet antes do endereço.
            affected.stream().map(tx -> Pet.findByAddress(tx.address)).flatMap(Optional::stream)
                    .map(pet -> pet.id).distinct().sorted().forEach(Pet::lockForUpdate);
            affected.forEach(tx -> lockAddress(tx.address));
            for (BitcoinTransaction tx : affected) {
                if (tx.status != BitcoinTransaction.Status.CONFIRMED) continue;
                AddressMonitorState state = getOrCreateState(tx.address, now);
                Long previousBalance = state.confirmedBalanceSats;
                var balance = indexer.getBalance(tx.address.canonical);
                if (balance.state() == BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE) providerFailure(tx.address, state, now);
                else state.recordBalance(balance.confirmedSats(), balance.pendingSats(), now);
                Map<String, Object> data = new java.util.LinkedHashMap<>(reorgEvent);
                data.putAll(reorgData(buildBlockMap(tx), previousBalance));
                tx.status = BitcoinTransaction.Status.PENDING;
                tx.confirmedAt = null; tx.blockHeight = null; tx.blockHash = null;
                if (tx.logicalReceipt == null) tx.logicalReceipt = LogicalReceipt.findByAddressAndTxid(tx.address, txid).orElse(null);
                updateReceipt(tx, now);
                if (balance.state() != BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE) publishBalance(tx.address, state, balance, now);
                emitChainReorgEvent(tx.address, tx, data, now);
            }
        }
    }

    private Map<String, Object> reorgData(Map<String, Object> previousBlock, Long previousBalance) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("previousBlock", previousBlock); data.put("previousConfirmedBalanceSats", previousBalance);
        return data;
    }

    private void lockAddress(Address address) {
        Pet.findByAddress(address).ifPresent(pet -> Pet.lockForUpdate(pet.id));
        var em = Address.getEntityManager();
        em.flush();
        Address managed = em.contains(address) ? address : em.find(Address.class, address.id);
        em.lock(managed, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    private Optional<BitcoinTransaction> findTransaction(Address address, String txid) {
        return BitcoinTransaction.find("address = ?1 and txid = ?2", address, txid).firstResultOptional();
    }

    private AddressMonitorState getOrCreateState(Address address, Instant now) {
        AddressMonitorState state = AddressMonitorState.loadOrCreate(address, now);
        var em = AddressMonitorState.getEntityManager();
        if (em.getLockMode(state) != jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) {
            em.flush();
            em.refresh(state, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        return state;
    }

    private void providerFailure(Address address, AddressMonitorState state, Instant now) {
        state.lastCheckedAt = now; state.providerAvailable = false;
        notifyPet(address, pet -> petLifecycle.onProviderFailure(pet.id, now));
    }

    private void publishBalance(Address address, AddressMonitorState state,
                                BitcoinIndexerPort.BalanceResult balance, Instant now) {
        if (!java.util.Objects.equals(state.lastPublishedConfirmedSats, balance.confirmedSats())
                || !java.util.Objects.equals(state.lastPublishedPendingSats, balance.pendingSats())) {
            emitReconciliationEvent(address, balance, now);
            state.lastPublishedConfirmedSats = balance.confirmedSats();
            state.lastPublishedPendingSats = balance.pendingSats();
        }
        notifyPet(address, pet -> petLifecycle.onBalanceKnown(pet.id, balance.confirmedSats(), balance.pendingSats(), now));
    }

    private void notifyPet(Address address, Consumer<Pet> action) {
        Pet.findByAddress(address).ifPresent(action);
    }

    // -------------------------------------------------------------------------
    // Emissão de eventos de domínio redagidos
    // -------------------------------------------------------------------------

    private void emitTransactionObservedEvent(
            Address address,
            BitcoinTransaction tx,
            BitcoinIndexerPort.TransactionInfo txInfo,
            Instant now
    ) {
        try {
            String network = detectNetwork(address.canonical);
            Map<String, Object> payload = Map.of(
                    "network", network,
                    "address", address.canonical,
                    "txid", tx.txid,
                    "status", tx.status.name(),
                    "source", "INDEXER",
                    "outputs", txInfo.outputs().stream()
                            .map(o -> Map.of("vout", o.vout(), "address", o.address(),
                                    "amountSats", o.amountSats()))
                            .toList(),
                    "totalReceivedSats", tx.amountSats,
                    "block", buildBlockMap(tx)
            );

            String json = redactor.redact(DomainEventType.BITCOIN_TRANSACTION_OBSERVED.value(), payload, null, null);
            outboxService.save(
                    UUID.randomUUID(),
                    BitcoinEventRedactor.AGGREGATE_TYPE,
                    address.canonical,
                    DomainEventType.BITCOIN_TRANSACTION_OBSERVED,
                    json,
                    null
            );
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir BITCOIN_TRANSACTION_OBSERVED para txid=%s", tx.txid);
            throw new IllegalStateException("Falha ao gravar evento BITCOIN_TRANSACTION_OBSERVED", e);
        }
    }

    private void emitTransactionConfirmedEvent(
            Address address,
            BitcoinTransaction tx,
            BitcoinTransaction.Status previousStatus,
            BitcoinIndexerPort.TransactionInfo txInfo,
            Instant now
    ) {
        try {
            String network = detectNetwork(address.canonical);
            Map<String, Object> payload = Map.of(
                    "network", network,
                    "address", address.canonical,
                    "txid", tx.txid,
                    "previousStatus", previousStatus.name(),
                    "status", tx.status.name(),
                    "source", "INDEXER",
                    "outputs", txInfo.outputs().stream()
                            .map(o -> Map.of("vout", o.vout(), "address", o.address(),
                                    "amountSats", o.amountSats()))
                            .toList(),
                    "totalReceivedSats", tx.amountSats,
                    "block", buildBlockMap(tx)
            );

            String json = redactor.redact(DomainEventType.BITCOIN_TRANSACTION_CONFIRMED.value(), payload, null, null);
            outboxService.save(
                    UUID.randomUUID(),
                    BitcoinEventRedactor.AGGREGATE_TYPE,
                    address.canonical,
                    DomainEventType.BITCOIN_TRANSACTION_CONFIRMED,
                    json,
                    null
            );
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir BITCOIN_TRANSACTION_CONFIRMED para txid=%s", tx.txid);
            throw new IllegalStateException("Falha ao gravar evento BITCOIN_TRANSACTION_CONFIRMED", e);
        }
    }

    private void emitTransactionReplacedEvent(
            Address address,
            BitcoinTransaction tx,
            BitcoinIndexerPort.TransactionInfo replacementInfo,
            Instant now
    ) {
        try {
            String network = detectNetwork(address.canonical);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("network", network);
            payload.put("address", address.canonical);
            payload.put("replacedTxid", tx.txid);
            payload.put("replacementTxid", replacementInfo.txid());
            payload.put("replacedStatus", BitcoinTransaction.Status.REPLACED.name());
            payload.put("replacementStatus", replacementInfo.status().name());
            payload.put("conflictInputs", BitcoinInput.inputsOf(tx).stream()
                    .filter(replacementInfo.inputs()::contains)
                    .map(input -> Map.of("txid", input.txid(), "vout", input.vout())).toList());
            payload.put("replacedReceivedSats", tx.amountSats);
            payload.put("replacementReceivedSats", replacementInfo.amountSats());

            String json = redactor.redact(DomainEventType.BITCOIN_TRANSACTION_REPLACED.value(), payload, null, null);
            outboxService.save(
                    UUID.randomUUID(),
                    BitcoinEventRedactor.AGGREGATE_TYPE,
                    address.canonical,
                    DomainEventType.BITCOIN_TRANSACTION_REPLACED,
                    json,
                    null
            );
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir BITCOIN_TRANSACTION_REPLACED para txid=%s", tx.txid);
            throw new IllegalStateException("Falha ao gravar evento BITCOIN_TRANSACTION_REPLACED", e);
        }
    }

    private void emitTransactionDroppedEvent(
            Address address,
            BitcoinTransaction tx,
            BitcoinTransaction.Status previousStatus,
            BitcoinIndexerPort.TransactionInfo txInfo,
            Instant now
    ) {
        try {
            String network = detectNetwork(address.canonical);
            Map<String, Object> evidence = Map.of(
                    "kind", "MEMPOOL_EVICTED",
                    "reconciledAt", now.toString(),
                    "tip", Map.of("hash", "", "height", 0)
            );
            Map<String, Object> payload = Map.of(
                    "network", network,
                    "address", address.canonical,
                    "txid", tx.txid,
                    "previousStatus", previousStatus.name(),
                    "status", BitcoinTransaction.Status.DROPPED.name(),
                    "reason", "MEMPOOL_EVICTED",
                    "evidence", evidence,
                    "invalidatedReceivedSats", tx.amountSats
            );

            String json = redactor.redact(DomainEventType.BITCOIN_TRANSACTION_DROPPED.value(), payload, null, null);
            outboxService.save(
                    UUID.randomUUID(),
                    BitcoinEventRedactor.AGGREGATE_TYPE,
                    address.canonical,
                    DomainEventType.BITCOIN_TRANSACTION_DROPPED,
                    json,
                    null
            );
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir BITCOIN_TRANSACTION_DROPPED para txid=%s", tx.txid);
            throw new IllegalStateException("Falha ao gravar evento BITCOIN_TRANSACTION_DROPPED", e);
        }
    }

    private void emitChainReorgEvent(
            Address address,
            BitcoinTransaction tx,
            Map<String, Object> reorgData,
            Instant now
    ) {
        try {
            String network = detectNetwork(address.canonical);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("network", network);
            payload.put("address", address.canonical);
            payload.put("oldTip", reorgData.get("oldTip"));
            payload.put("newTip", reorgData.get("newTip"));
            payload.put("forkHeight", reorgData.get("forkHeight"));
            payload.put("depth", reorgData.get("depth"));
            payload.put("affectedTransactions", List.of(
                    Map.of("txid", tx.txid, "previousStatus", "CONFIRMED", "currentStatus", tx.status.name(),
                            "previousBlock", reorgData.getOrDefault("previousBlock", Map.of()), "currentBlock", buildBlockMap(tx))
            ));
            payload.put("previousConfirmedBalanceSats", reorgData.get("previousConfirmedBalanceSats"));
            payload.put("currentConfirmedBalanceSats", AddressMonitorState.findByAddress(address)
                    .filter(state -> state.providerAvailable).map(state -> state.confirmedBalanceSats).orElse(null));

            String json = redactor.redact(DomainEventType.BITCOIN_CHAIN_REORG.value(), payload, null, null);
            outboxService.save(
                    UUID.randomUUID(),
                    BitcoinEventRedactor.AGGREGATE_TYPE,
                    address.canonical,
                    DomainEventType.BITCOIN_CHAIN_REORG,
                    json,
                    null
            );
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir BITCOIN_CHAIN_REORG para txid=%s", tx.txid);
            throw new IllegalStateException("Falha ao gravar evento BITCOIN_CHAIN_REORG", e);
        }
    }

    private void emitReconciliationEvent(
            Address address,
            BitcoinIndexerPort.BalanceResult balance,
            Instant now
    ) {
        try {
            String network = detectNetwork(address.canonical);
            Map<String, Object> payload = Map.of(
                    "network", network,
                    "address", address.canonical,
                    "balanceStatus", balance.state().name(),
                    "confirmedBalanceSats", balance.confirmedSats(),
                    "pendingIncomingSats", Math.max(0, balance.pendingSats()),
                    "pendingOutgoingSats", 0L,
                    "transactionsReconciled", 0,
                    "reconciliationTip", Map.of("hash", "", "height", 0)
            );

            String json = redactor.redact(DomainEventType.BITCOIN_BALANCE_RECONCILED.value(), payload, null, null);
            outboxService.save(
                    UUID.randomUUID(),
                    BitcoinEventRedactor.AGGREGATE_TYPE,
                    address.canonical,
                    DomainEventType.BITCOIN_BALANCE_RECONCILED,
                    json,
                    null
            );
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir BITCOIN_BALANCE_RECONCILED para endereço=%s", address.canonical);
            throw new IllegalStateException("Falha ao gravar evento BITCOIN_BALANCE_RECONCILED", e);
        }
    }

    // -------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------

    private Map<String, Object> buildBlockMap(BitcoinTransaction tx) {
        if (tx.blockHash == null) return Map.of();
        return Map.of(
                "hash", tx.blockHash,
                "height", tx.blockHeight != null ? tx.blockHeight : 0,
                "confirmations", 1
        );
    }

    private String detectNetwork(String canonical) {
        if (canonical == null) return "unknown";
        String lower = canonical.toLowerCase();
        if (lower.startsWith("bc1") || lower.startsWith("1") || lower.startsWith("3")) return "mainnet";
        if (lower.startsWith("tb1") || lower.startsWith("2") || lower.startsWith("m") || lower.startsWith("n")) return "testnet";
        if (lower.startsWith("bcrt1")) return "regtest";
        return "unknown";
    }
}
