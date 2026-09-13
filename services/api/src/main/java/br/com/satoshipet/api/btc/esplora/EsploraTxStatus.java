package br.com.satoshipet.api.btc.esplora;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Status de confirmação de uma transação na API Esplora. */
public record EsploraTxStatus(
        boolean confirmed,
        @JsonProperty("block_height") Integer blockHeight,
        @JsonProperty("block_hash") String blockHash,
        @JsonProperty("block_time") Long blockTime
) {}
