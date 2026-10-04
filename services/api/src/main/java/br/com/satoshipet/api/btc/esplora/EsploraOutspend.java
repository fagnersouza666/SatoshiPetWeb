package br.com.satoshipet.api.btc.esplora;

/** Evidência de consumo de um outpoint consultada no Esplora. */
public record EsploraOutspend(boolean spent, String txid, Integer vin, EsploraTxStatus status) {}
