package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.btc.esplora.EsploraAddressStats;
import br.com.satoshipet.api.btc.esplora.EsploraTx;
import br.com.satoshipet.api.btc.esplora.EsploraTxOutput;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador do indexador Bitcoin usando a API pública Blockstream Esplora.
 *
 * <p>Ativo em todos os perfis, exceto {@code test} — substituído por
 * {@link StubBitcoinIndexer} no perfil de testes.</p>
 *
 * <p>Nunca lança exceção: falhas de rede retornam
 * {@link BitcoinIndexerPort.BalanceState#PROVIDER_FAILURE} (CA-031).</p>
 */
@ApplicationScoped
@UnlessBuildProfile("test")
public class EsploraBitcoinIndexer implements BitcoinIndexerPort {

    private static final Logger LOG = Logger.getLogger(EsploraBitcoinIndexer.class);

    @RestClient
    EsploraClient esplora;

    @Override
    public BalanceResult getBalance(String canonical) {
        try {
            EsploraAddressStats stats = esplora.getAddress(canonical);
            long confirmed = stats.chainStats().fundedTxoSum()
                    - stats.chainStats().spentTxoSum();
            long pending   = stats.mempoolStats().fundedTxoSum()
                    - stats.mempoolStats().spentTxoSum();

            BalanceState state = (stats.chainStats().txCount() == 0
                    && stats.mempoolStats().txCount() == 0)
                    ? BalanceState.UNKNOWN
                    : BalanceState.CONFIRMED;

            return new BalanceResult(state, Math.max(0, confirmed), Math.max(0, pending));
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao consultar saldo no Esplora para endereço=%s", canonical);
            return new BalanceResult(BalanceState.PROVIDER_FAILURE, 0L, 0L);
        }
    }

    @Override
    public List<TransactionInfo> getTransactions(String canonical, String cursor, int limit) {
        return getTransactionPage(canonical, cursor, limit).transactions();
    }

    @Override
    public TransactionPage getTransactionPage(String canonical, String cursor, int limit) {
        try {
            List<EsploraTx> txs = cursor == null || cursor.isBlank()
                    ? esplora.getChainTransactionsFromStart(canonical)
                    : esplora.getChainTransactions(canonical, cursor);
            return new TransactionPage(txs.stream()
                    .map(tx -> toTransactionInfo(tx, canonical))
                    .limit(limit)
                    .toList(), true);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao listar transações on-chain no Esplora para endereço=%s", canonical);
            return new TransactionPage(List.of(), false);
        }
    }

    @Override
    public List<TransactionInfo> getMempool(String canonical) {
        return getMempoolPage(canonical).transactions();
    }

    @Override
    public TransactionPage getMempoolPage(String canonical) {
        try {
            return new TransactionPage(esplora.getMempoolTransactions(canonical).stream()
                    .map(tx -> toTransactionInfo(tx, canonical))
                    .toList(), true);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao listar mempool no Esplora para endereço=%s", canonical);
            return new TransactionPage(List.of(), false);
        }
    }

    @Override
    public TransactionLookup getTransaction(String canonical, String txid) {
        try {
            EsploraTx tx = esplora.getTransaction(txid);
            if (tx == null || tx.status() == null || !txid.equals(tx.txid())) {
                return new TransactionLookup(LookupState.UNAVAILABLE, null);
            }
            return new TransactionLookup(LookupState.FOUND, toTransactionInfo(tx, canonical));
        } catch (jakarta.ws.rs.NotFoundException e) {
            return new TransactionLookup(LookupState.MISSING, null);
        } catch (Exception e) {
            LOG.warnf("Falha ao reconciliar transação %s no Esplora", txid);
            return new TransactionLookup(LookupState.UNAVAILABLE, null);
        }
    }

    @Override
    public OutspendLookup getOutspend(InputInfo input) {
        try {
            var spend = esplora.getOutspend(input.txid(), input.vout());
            if (spend == null || (spend.spent() && spend.txid() == null)) {
                return new OutspendLookup(false, null);
            }
            return new OutspendLookup(true, spend.spent() ? spend.txid() : null);
        } catch (Exception e) {
            LOG.warnf("Falha ao reconciliar outpoint %s:%d no Esplora", input.txid(), input.vout());
            return new OutspendLookup(false, null);
        }
    }

    // -------------------------------------------------------------------------
    // Mapeamento
    // -------------------------------------------------------------------------

    private TransactionInfo toTransactionInfo(EsploraTx tx, String canonical) {
        boolean confirmed = tx.status() != null && tx.status().confirmed();
        BitcoinTransaction.Status status = confirmed
                ? BitcoinTransaction.Status.CONFIRMED
                : BitcoinTransaction.Status.PENDING;

        Instant confirmedAt = null;
        Integer blockHeight = null;
        String blockHash    = null;

        if (confirmed && tx.status() != null) {
            blockHeight = tx.status().blockHeight();
            blockHash   = tx.status().blockHash();
            if (tx.status().blockTime() != null) {
                confirmedAt = Instant.ofEpochSecond(tx.status().blockTime());
            }
        }

        // Filtra outputs destinados ao endereço monitorado
        List<OutputInfo> outputs = new ArrayList<>();
        long amountSats = 0L;

        if (tx.vout() != null) {
            for (int i = 0; i < tx.vout().size(); i++) {
                EsploraTxOutput out = tx.vout().get(i);
                if (canonical.equals(out.scriptpubkeyAddress())) {
                    outputs.add(new OutputInfo(i, out.scriptpubkeyAddress(), out.value()));
                    amountSats += out.value();
                }
            }
        }

        return new TransactionInfo(
                tx.txid(),
                amountSats,
                status,
                Instant.now(),  // Esplora não fornece o momento exato da observação
                confirmedAt,
                blockHeight,
                blockHash,
                outputs,
                tx.vin() == null ? List.of() : tx.vin().stream()
                        .filter(input -> input.prevTxid() != null)
                        .map(input -> new InputInfo(input.prevTxid(), input.prevVout())).toList()
        );
    }
}
