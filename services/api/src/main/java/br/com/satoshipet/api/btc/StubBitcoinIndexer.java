package br.com.satoshipet.api.btc;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub determinístico do indexador Bitcoin para o perfil de testes.
 *
 * <p>Ativo apenas no perfil {@code test}. Permite que testes configurem
 * cenários programaticamente via {@link #setBalance} e {@link #addTransaction}.</p>
 *
 * <p>Não realiza chamadas de rede.</p>
 */
@ApplicationScoped
@IfBuildProfile("test")
public class StubBitcoinIndexer implements BitcoinIndexerPort {

    private static String key(String canonical) {
        String lower = canonical.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("bc1") || lower.startsWith("tb1") || lower.startsWith("bcrt1") ? lower : canonical;
    }

    /** Saldo configurável por endereço. */
    private final Map<String, BalanceResult> balances = new ConcurrentHashMap<>();

    /** Transações on-chain por endereço. */
    private final Map<String, List<TransactionInfo>> chainTxs = new ConcurrentHashMap<>();

    /** Transações pendentes (mempool) por endereço. */
    private final Map<String, List<TransactionInfo>> mempoolTxs = new ConcurrentHashMap<>();

    private final Map<String, TransactionLookup> lookups = new ConcurrentHashMap<>();
    private final Map<InputInfo, OutspendLookup> spends = new ConcurrentHashMap<>();
    private final java.util.Set<String> failedPages = ConcurrentHashMap.newKeySet();

    public void setTransactionLookup(String canonical, String txid, TransactionLookup lookup) {
        lookups.put(canonical + ":" + txid, lookup);
    }
    public void setOutspend(InputInfo input, OutspendLookup lookup) { spends.put(input, lookup); }
    public void failPage(String canonical, String cursor) { failedPages.add(canonical + ":" + cursor); }

    @Override
    public TransactionLookup getTransaction(String canonical, String txid) {
        TransactionLookup configured = lookups.get(canonical + ":" + txid);
        if (configured != null) return configured;
        return java.util.stream.Stream.concat(getTransactions(canonical, null, Integer.MAX_VALUE).stream(),
                getMempool(canonical).stream()).filter(tx -> tx.txid().equals(txid)).findFirst()
                .map(tx -> new TransactionLookup(LookupState.FOUND, tx))
                .orElseGet(() -> new TransactionLookup(LookupState.MISSING, null));
    }
    @Override
    public OutspendLookup getOutspend(InputInfo input) {
        return spends.getOrDefault(input, new OutspendLookup(true, null));
    }
    @Override
    public TransactionPage getTransactionPage(String canonical, String cursor, int limit) {
        return failedPages.contains(canonical + ":" + cursor) ? new TransactionPage(List.of(), false)
                : new TransactionPage(getTransactions(canonical, cursor, limit), true);
    }

    // -------------------------------------------------------------------------
    // Configuração (API de testes)
    // -------------------------------------------------------------------------

    /**
     * Define o saldo que será retornado para o endereço informado.
     *
     * @param canonical    endereço canônico
     * @param balanceResult saldo a retornar
     */
    public void setBalance(String canonical, BalanceResult balanceResult) {
        balances.put(key(canonical), balanceResult);
    }

    /**
     * Adiciona uma transação on-chain para o endereço informado.
     *
     * @param canonical endereço canônico
     * @param tx        informações da transação
     */
    public void addTransaction(String canonical, TransactionInfo tx) {
        chainTxs.computeIfAbsent(key(canonical), k -> new ArrayList<>()).add(tx);
    }

    /**
     * Adiciona uma transação pendente (mempool) para o endereço informado.
     *
     * @param canonical endereço canônico
     * @param tx        informações da transação
     */
    public void addMempoolTransaction(String canonical, TransactionInfo tx) {
        mempoolTxs.computeIfAbsent(key(canonical), k -> new ArrayList<>()).add(tx);
    }

    /**
     * Remove todos os dados configurados (útil entre testes).
     */
    public void reset() {
        lookups.clear();
        spends.clear();
        failedPages.clear();
        balances.clear();
        chainTxs.clear();
        mempoolTxs.clear();
    }

    /**
     * Remove dados de um endereço específico.
     *
     * @param canonical endereço canônico
     */
    public void resetAddress(String canonical) {
        String key = key(canonical);
        balances.remove(key);
        chainTxs.remove(key);
        mempoolTxs.remove(key);
    }

    // -------------------------------------------------------------------------
    // BitcoinIndexerPort
    // -------------------------------------------------------------------------

    @Override
    public BalanceResult getBalance(String canonical) {
        return balances.getOrDefault(
                key(canonical),
                new BalanceResult(BalanceState.UNKNOWN, 0L, 0L)
        );
    }

    @Override
    public List<TransactionInfo> getTransactions(String canonical, String cursor, int limit) {
        List<TransactionInfo> all = chainTxs.getOrDefault(key(canonical), List.of());

        if (cursor == null || cursor.isBlank()) {
            return all.stream().limit(limit).toList();
        }

        // Retorna transações após o cursor (exclusive)
        boolean found = false;
        List<TransactionInfo> result = new ArrayList<>();
        for (TransactionInfo tx : all) {
            if (found) {
                result.add(tx);
                if (result.size() >= limit) break;
            }
            if (tx.txid().equals(cursor)) {
                found = true;
            }
        }
        // Se cursor não for encontrado, retorna tudo
        return found ? Collections.unmodifiableList(result) : all.stream().limit(limit).toList();
    }

    @Override
    public List<TransactionInfo> getMempool(String canonical) {
        return Collections.unmodifiableList(
                mempoolTxs.getOrDefault(key(canonical), List.of())
        );
    }
}
