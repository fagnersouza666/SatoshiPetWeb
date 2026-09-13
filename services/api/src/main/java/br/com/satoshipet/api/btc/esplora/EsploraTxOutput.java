package br.com.satoshipet.api.btc.esplora;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Saída de transação na API Esplora. */
public record EsploraTxOutput(
        String scriptpubkey,
        @JsonProperty("scriptpubkey_asm") String scriptpubkeyAsm,
        @JsonProperty("scriptpubkey_type") String scriptpubkeyType,
        @JsonProperty("scriptpubkey_address") String scriptpubkeyAddress,
        long value
) {}
