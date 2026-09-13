package br.com.satoshipet.api.btc.esplora;

import java.util.List;

/** Transação completa retornada pela API Esplora. */
public record EsploraTx(
        String txid,
        int version,
        int locktime,
        List<EsploraTxInput> vin,
        List<EsploraTxOutput> vout,
        long size,
        long weight,
        long fee,
        EsploraTxStatus status
) {}
