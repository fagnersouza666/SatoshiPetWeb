package br.com.satoshipet.api.btc;

import java.time.Instant;
import java.util.List;

/**
 * Porta de saída para o indexador de transações Bitcoin (PRD §4, CC-05).
 *
 * <p>Abstrai o acesso ao Blockstream Esplora ou equivalente.
 * Nunca lança exceção: falhas de provedor retornam {@link BalanceState#PROVIDER_FAILURE} (CA-031).</p>
 *
 * <p>Implementações:
 * <ul>
 *   <li>{@link EsploraBitcoinIndexer} — REST client Esplora (produção)</li>
 *   <li>{@link StubBitcoinIndexer} — fixture determinística (testes)</li>
 * </ul></p>
 */
public interface BitcoinIndexerPort {

    /**
     * Estado possível do saldo de um endereço no indexador.
     */
    enum BalanceState {
        /** Saldo confirmado disponível; dado confiável. */
        CONFIRMED,
        /** Endereço sem histórico ou saldo zero confirmado. */
        UNKNOWN,
        /** Provedor inacessível ou retornou erro — não alterar estado local (CA-031). */
        PROVIDER_FAILURE
    }

    /**
     * Resultado de consulta de saldo.
     *
     * @param state         estado da consulta
     * @param confirmedSats saldo confirmado em satoshis (0 em caso de UNKNOWN/PROVIDER_FAILURE)
     * @param pendingSats   saldo pendente na mempool em satoshis
     */
    record BalanceResult(BalanceState state, long confirmedSats, long pendingSats) {}

    /**
     * Informações de uma saída (UTXO) de transação.
     *
     * @param vout      índice da saída na transação
     * @param address   endereço de destino (canônico)
     * @param amountSats valor em satoshis
     */
    record OutputInfo(int vout, String address, long amountSats) {}

    /** Outpoint consumido: evidência de conflito, nunca endereço/valor. */
    record InputInfo(String txid, int vout) {}

    enum LookupState { FOUND, MISSING, UNAVAILABLE }
    record TransactionLookup(LookupState state, TransactionInfo transaction) {}
    /** available=false inclui 404: ausência do prevout não prova descarte. */
    record OutspendLookup(boolean available, String spendingTxid) {}

    default TransactionLookup getTransaction(String canonical, String txid) {
        return new TransactionLookup(LookupState.MISSING, null);
    }

    default OutspendLookup getOutspend(InputInfo input) {
        return new OutspendLookup(false, null);
    }

    /**
     * Informações de uma transação retornadas pelo indexador.
     *
     * @param txid        hash da transação em hex (64 chars)
     * @param amountSats  valor total recebido para o endereço monitorado (sats)
     * @param status      estado atual da transação (PENDING ou CONFIRMED)
     * @param observedAt  momento da primeira observação
     * @param confirmedAt momento da confirmação em bloco (null se pendente)
     * @param blockHeight altura do bloco de confirmação (null se pendente)
     * @param blockHash   hash do bloco de confirmação (null se pendente)
     * @param outputs     saídas da transação relevantes ao endereço monitorado
     */
    record TransactionInfo(
            String txid,
            long amountSats,
            BitcoinTransaction.Status status,
            Instant observedAt,
            Instant confirmedAt,
            Integer blockHeight,
            String blockHash,
            List<OutputInfo> outputs,
            List<InputInfo> inputs
    ) {
        public TransactionInfo {
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
        }
        public TransactionInfo(String txid, long amountSats, BitcoinTransaction.Status status,
                Instant observedAt, Instant confirmedAt, Integer blockHeight, String blockHash,
                List<OutputInfo> outputs) {
            this(txid, amountSats, status, observedAt, confirmedAt, blockHeight, blockHash, outputs, List.of());
        }
    }

    /**
     * Consulta o saldo de um endereço.
     * Nunca lança exceção — em caso de falha retorna {@link BalanceState#PROVIDER_FAILURE} (CA-031).
     *
     * @param canonical forma canônica do endereço Bitcoin
     * @return resultado com estado e saldos
     */
    BalanceResult getBalance(String canonical);

    /**
     * Retorna transações on-chain (confirmadas) para o endereço, paginadas por cursor.
     *
     * @param canonical forma canônica do endereço
     * @param cursor    último txid observado para paginação (null para início)
     * @param limit     quantidade máxima de transações a retornar
     * @return lista de transações confirmadas
     */
    List<TransactionInfo> getTransactions(String canonical, String cursor, int limit);

    /** Página vazia bem-sucedida encerra histórico; falha mantém cursor para retry. */
    record TransactionPage(List<TransactionInfo> transactions, boolean available) {
        public TransactionPage { transactions = List.copyOf(transactions); }
    }

    default TransactionPage getTransactionPage(String canonical, String cursor, int limit) {
        return new TransactionPage(getTransactions(canonical, cursor, limit), true);
    }

    default TransactionPage getMempoolPage(String canonical) {
        return new TransactionPage(getMempool(canonical), true);
    }

    /**
     * Retorna transações pendentes na mempool para o endereço.
     *
     * @param canonical forma canônica do endereço
     * @return lista de transações pendentes na mempool
     */
    List<TransactionInfo> getMempool(String canonical);
}
