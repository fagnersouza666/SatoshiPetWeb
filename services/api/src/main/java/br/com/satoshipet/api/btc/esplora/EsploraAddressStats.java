package br.com.satoshipet.api.btc.esplora;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Estatísticas de saldo de um endereço (chain + mempool) na API Esplora. */
public record EsploraAddressStats(
        String address,
        @JsonProperty("chain_stats") EsploraChainStats chainStats,
        @JsonProperty("mempool_stats") EsploraChainStats mempoolStats
) {}
