package br.com.satoshipet.api.btc;

import java.time.Instant;
import java.util.List;

/**
 * DTO público de endereço Bitcoin.
 *
 * <p>Somente campos autorizados pela política de redação pública (CA-009).
 * Nunca expõe accountId, petId, bindingId, email ou qualquer campo da
 * denylist definida em {@code redaction-policy.json}.</p>
 *
 * @param address            forma canônica do endereço Bitcoin
 * @param network            rede ("mainnet", "testnet", "regtest")
 * @param confirmedSats      saldo confirmado em satoshis
 * @param pendingSats        saldo pendente (mempool) em satoshis
 * @param transactionCount   número total de transações observadas
 * @param recentTransactions lista de transações recentes (limitada)
 * @param qrData             URI para QR code: {@code bitcoin:{address}}
 * @param explorerUrl        URL do block explorer público
 */
public record PublicAddressResponse(
        String address,
        String network,
        long confirmedSats,
        long pendingSats,
        int transactionCount,
        List<TransactionSummary> recentTransactions,
        String qrData,
        String explorerUrl
) {
    public PublicAddressResponse {
        recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
    }

    /**
     * Resumo público de uma transação.
     *
     * @param txid        hash da transação
     * @param status      estado: PENDING, CONFIRMED, REPLACED, DROPPED
     * @param amountSats  valor recebido em satoshis
     * @param observedAt  momento da primeira observação
     * @param confirmedAt momento da confirmação (null se pendente)
     * @param blockHeight altura do bloco (null se pendente)
     */
    public record TransactionSummary(
            String txid,
            String status,
            long amountSats,
            Instant observedAt,
            Instant confirmedAt,
            Integer blockHeight
    ) {}
}
