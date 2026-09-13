package br.com.satoshipet.api.btc.esplora;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Estatísticas de UTXOs de um conjunto (chain ou mempool) na API Esplora. */
public record EsploraChainStats(
        @JsonProperty("funded_txo_count") int fundedTxoCount,
        @JsonProperty("funded_txo_sum") long fundedTxoSum,
        @JsonProperty("spent_txo_count") int spentTxoCount,
        @JsonProperty("spent_txo_sum") long spentTxoSum,
        @JsonProperty("tx_count") int txCount
) {}
