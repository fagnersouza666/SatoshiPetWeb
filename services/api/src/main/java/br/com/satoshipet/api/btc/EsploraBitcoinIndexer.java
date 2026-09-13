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
        try {
            List<EsploraTx> txs = cursor == null || cursor.isBlank()
                    ? esplora.getChainTransactionsFromStart(canonical)
                    : esplora.getChainTransactions(canonical, cursor);
            return txs.stream()
                    .map(tx -> toTransactionInfo(tx, canonical))
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao listar transações on-chain no Esplora para endereço=%s", canonical);
            return List.of();
        }
    }

    @Override
    public List<TransactionInfo> getMempool(String canonical) {
        try {
            return esplora.getMempoolTransactions(canonical).stream()
                    .map(tx -> toTransactionInfo(tx, canonical))
                    .toList();
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao listar mempool no Esplora para endereço=%s", canonical);
            return List.of();
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
                if (canonical.equalsIgnoreCase(out.scriptpubkeyAddress())) {
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
                outputs
        );
    }
}
