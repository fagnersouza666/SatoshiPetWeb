package br.com.satoshipet.api.btc.esplora;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Entrada de transação na API Esplora. */
public record EsploraTxInput(
        @JsonProperty("txid") String prevTxid,
        @JsonProperty("vout") int prevVout,
        @JsonAlias("prevout") EsploraTxOutput prevout,
        long sequence
) {}
