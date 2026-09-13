package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.events.DomainEventType;
import br.com.satoshipet.api.outbox.OutboxService;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetLifecyclePort;
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
 *   <li>Eventos de compra declarada NUNCA disparam {@code applyFeeding}.</li>
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
        Instant now = Instant.now();
        LOG.debugf("Polling endereço=%s", address.canonical);

        // 1. Obtém ou cria estado do monitor para este endereço
        AddressMonitorState state = getOrCreateState(address, now);

        // 2. Consulta saldo (CA-031: nunca falha, retorna estado PROVIDER_FAILURE)
        BitcoinIndexerPort.BalanceResult balance = indexer.getBalance(address.canonical);
        if (balance.state() == BitcoinIndexerPort.BalanceState.PROVIDER_FAILURE) {
            LOG.warnf("Provedor inacessível para endereço=%s — estado local preservado", address.canonical);
            state.lastCheckedAt = now;
            return; // CA-031: falha de provedor não altera estado local
        }

        // 3. Obtém transações pendentes na mempool
        List<BitcoinIndexerPort.TransactionInfo> mempoolTxs = indexer.getMempool(address.canonical);

        // 4. Obtém transações on-chain (paginadas a partir do cursor)
        List<BitcoinIndexerPort.TransactionInfo> chainTxs =
                indexer.getTransactions(address.canonical, state.lastSeenTxid, TX_PAGE_SIZE);

        // 5. Processa todas as transações (mempool primeiro, depois on-chain)
        List<BitcoinIndexerPort.TransactionInfo> allTxs = new ArrayList<>(mempoolTxs);
        allTxs.addAll(chainTxs);

        String newLastSeen = state.lastSeenTxid;
        for (BitcoinIndexerPort.TransactionInfo txInfo : allTxs) {
            processTransaction(address, txInfo, now);
            if (!chainTxs.isEmpty() && txInfo.equals(chainTxs.get(chainTxs.size() - 1))) {
                newLastSeen = txInfo.txid();
            }
        }

        // 6. Avança o cursor do estado do monitor
        state.advance(newLastSeen, newLastSeen, now);

        // 7. Emite evento de reconciliação quando saldo confirmado disponível
        if (balance.state() == BitcoinIndexerPort.BalanceState.CONFIRMED) {
            emitReconciliationEvent(address, balance, now);
        }
    }

    // -------------------------------------------------------------------------
    // Processamento individual de transações
    // -------------------------------------------------------------------------

    private void processTransaction(
            Address address,
            BitcoinIndexerPort.TransactionInfo txInfo,
            Instant now
    ) {
        Optional<BitcoinTransaction> existing = BitcoinTransaction.findByTxid(txInfo.txid());

        if (existing.isEmpty()) {
            createTransaction(address, txInfo, now);
        } else {
            updateTransactionIfChanged(existing.get(), txInfo, now);
        }
    }

    private void createTransaction(
            Address address,
            BitcoinIndexerPort.TransactionInfo txInfo,
            Instant now
    ) {
        Instant observedAt = txInfo.observedAt() != null ? txInfo.observedAt() : now;

        BitcoinTransaction tx = BitcoinTransaction.createPending(
                txInfo.txid(), address, txInfo.amountSats(), observedAt
        );

        if (txInfo.status() == BitcoinTransaction.Status.CONFIRMED) {
            tx.status       = BitcoinTransaction.Status.CONFIRMED;
            tx.confirmedAt  = txInfo.confirmedAt() != null ? txInfo.confirmedAt() : now;
            tx.blockHeight  = txInfo.blockHeight();
            tx.blockHash    = txInfo.blockHash();
        }

        tx.persist();

        // Persiste outputs destinados ao endereço monitorado
        for (BitcoinIndexerPort.OutputInfo out : txInfo.outputs()) {
            BitcoinOutput output = BitcoinOutput.create(tx, out.vout(), address, out.amountSats(), null);
            output.persist();
        }

        // Cria ou atualiza LogicalReceipt idempotentemente
        LogicalReceipt receipt = LogicalReceipt.findByAddressAndTxid(address, txInfo.txid())
                .orElseGet(() -> {
                    LogicalReceipt r = LogicalReceipt.createPending(
                            address, txInfo.txid(), txInfo.amountSats(), now
                    );
                    r.persist();
                    return r;
                });

        if (txInfo.status() == BitcoinTransaction.Status.CONFIRMED) {
            receipt.confirmedSats = txInfo.amountSats();
            receipt.pendingSats   = 0L;
            receipt.updatedAt     = now;
        }

        // Notifica pet lifecycle com recebimento on-chain real (invariante crítica)
        notifyPetFeeding(address, txInfo.amountSats(), now);

        // Emite evento de domínio redagido
        emitTransactionObservedEvent(address, tx, txInfo, now);

        LOG.infof("Nova transação persistida: txid=%s endereço=%s sats=%d status=%s",
                txInfo.txid(), address.canonical, txInfo.amountSats(), tx.status);
    }

    private void updateTransactionIfChanged(
            BitcoinTransaction tx,
            BitcoinIndexerPort.TransactionInfo txInfo,
            Instant now
    ) {
        if (tx.status == txInfo.status()) return; // sem mudança

        BitcoinTransaction.Status previousStatus = tx.status;

        switch (txInfo.status()) {
            case CONFIRMED -> {
                tx.status      = BitcoinTransaction.Status.CONFIRMED;
                tx.confirmedAt = txInfo.confirmedAt() != null ? txInfo.confirmedAt() : now;
                tx.blockHeight = txInfo.blockHeight();
                tx.blockHash   = txInfo.blockHash();

                // Atualiza LogicalReceipt
                LogicalReceipt.findByAddressAndTxid(tx.address, tx.txid).ifPresent(r -> {
                    r.confirmedSats = tx.amountSats;
                    r.pendingSats   = 0L;
                    r.updatedAt     = now;
                });

                emitTransactionConfirmedEvent(tx.address, tx, previousStatus, txInfo, now);
                LOG.infof("Transação confirmada: txid=%s bloco=%d", tx.txid, tx.blockHeight);
            }
            case REPLACED -> {
                tx.status = BitcoinTransaction.Status.REPLACED;

                // Zera saldo pendente do recebimento lógico (sem duplicar)
                LogicalReceipt.findByAddressAndTxid(tx.address, tx.txid).ifPresent(r -> {
                    r.pendingSats = 0L;
                    r.updatedAt   = now;
                });

                emitTransactionReplacedEvent(tx.address, tx, txInfo, now);
                LOG.infof("Transação substituída (RBF): txid=%s", tx.txid);
            }
            case DROPPED -> {
                tx.status = BitcoinTransaction.Status.DROPPED;

                // Zera saldos pendentes; recalcula sem reescrever histórico (CC-10)
                LogicalReceipt.findByAddressAndTxid(tx.address, tx.txid).ifPresent(r -> {
                    r.pendingSats = 0L;
                    r.updatedAt   = now;
                });

                emitTransactionDroppedEvent(tx.address, tx, previousStatus, txInfo, now);
                LOG.infof("Transação descartada: txid=%s", tx.txid);
            }
            default -> LOG.warnf("Transição de status não tratada: %s → %s para txid=%s",
                    previousStatus, txInfo.status(), tx.txid);
        }
    }

    // -------------------------------------------------------------------------
    // Reorg handler (mínimo mas testado)
    // -------------------------------------------------------------------------

    /**
     * Processa uma reorganização de chain: uma transação que estava confirmada
     * volta à mempool.
     *
     * <p>Atualiza o status para PENDING, recalcula saldos do
     * {@link LogicalReceipt} sem reescrever histórico (CC-10).</p>
     *
     * @param txid       hash da transação afetada pelo reorg
     * @param reorgEvent dados do evento de reorganização
     * @param now        instante atual
     */
    @Transactional
    public void handleReorg(String txid, Map<String, Object> reorgEvent, Instant now) {
        BitcoinTransaction.findByTxid(txid).ifPresent(tx -> {
            if (tx.status != BitcoinTransaction.Status.CONFIRMED) return;

            LOG.warnf("Reorg detectado: txid=%s retorna à mempool", txid);
            tx.status      = BitcoinTransaction.Status.PENDING;
            tx.confirmedAt = null;
            tx.blockHeight = null;
            tx.blockHash   = null;

            LogicalReceipt.findByAddressAndTxid(tx.address, txid).ifPresent(r -> {
                r.pendingSats   = r.confirmedSats;
                r.confirmedSats = 0L;
                r.updatedAt     = now.isBefore(r.createdAt) ? r.createdAt : now;
            });

            emitChainReorgEvent(tx.address, tx, reorgEvent, now);
        });
    }

    // -------------------------------------------------------------------------
    // Estado do monitor
    // -------------------------------------------------------------------------

    private AddressMonitorState getOrCreateState(Address address, Instant now) {
        return AddressMonitorState.findByAddress(address)
                .orElseGet(() -> {
                    AddressMonitorState s = AddressMonitorState.init(address, now);
                    s.persist();
                    return s;
                });
    }

    // -------------------------------------------------------------------------
    // Pet lifecycle (invariante crítica: somente recebimentos on-chain reais)
    // -------------------------------------------------------------------------

    private void notifyPetFeeding(Address address, long amountSats, Instant now) {
        try {
            Pet.findByAddress(address).ifPresent(pet ->
                    petLifecycle.applyFeeding(pet.id, amountSats, now)
            );
        } catch (Exception e) {
            // Falha no pet lifecycle NÃO deve reverter a transação de monitoramento
            LOG.errorf(e, "Falha ao notificar pet lifecycle para endereço=%s", address.canonical);
        }
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
            payload.put("replacementStatus", BitcoinTransaction.Status.PENDING.name());
            payload.put("conflictInputs", List.of());
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
            payload.put("oldTip", reorgData.getOrDefault("oldTip", Map.of("hash", "", "height", 0)));
            payload.put("newTip", reorgData.getOrDefault("newTip", Map.of("hash", "", "height", 0)));
            payload.put("forkHeight", reorgData.getOrDefault("forkHeight", 0));
            payload.put("depth", reorgData.getOrDefault("depth", 1));
            payload.put("affectedTransactions", List.of(
                    Map.of("txid", tx.txid, "previousStatus", "CONFIRMED", "currentStatus", "PENDING",
                            "previousBlock", Map.of(), "currentBlock", Map.of())
            ));
            payload.put("previousConfirmedBalanceSats", tx.amountSats);
            payload.put("currentConfirmedBalanceSats", 0L);

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
