package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.pet.PetPublicSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * DTO público de endereço Bitcoin.
 *
 * <p>Somente campos autorizados pela política de redação pública (CA-009).
 * Nunca expõe accountId, petId, bindingId, email ou qualquer campo da
 * denylist definida em {@code redaction-policy.json}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicAddressResponse(
        String address,
        String network,
        long confirmedSats,
        long pendingSats,
        int transactionCount,
        List<TransactionSummary> recentTransactions,
        String qrData,
        String explorerUrl,
        String petName,
        String presentation,
        String petState,
        String reserveHours,
        Boolean awaitingReference,
        Boolean pendingMovesEgg,
        String operationalLabel,
        String artworkVersion,
        String atlasUrl
) {
    public PublicAddressResponse {
        recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
    }

    public static PublicAddressResponse of(
            String address,
            String network,
            long confirmedSats,
            long pendingSats,
            int transactionCount,
            List<TransactionSummary> recentTransactions,
            String qrData,
            String explorerUrl,
            PetPublicSnapshot pet
    ) {
        PetPublicSnapshot snapshot = pet == null ? PetPublicSnapshot.empty() : pet;
        return new PublicAddressResponse(
                address,
                network,
                confirmedSats,
                pendingSats,
                transactionCount,
                recentTransactions,
                qrData,
                explorerUrl,
                snapshot.petName(),
                snapshot.presentation(),
                snapshot.petState(),
                snapshot.reserveHours(),
                snapshot.awaitingReference(),
                snapshot.pendingMovesEgg(),
                snapshot.operationalLabel(),
                snapshot.artworkVersion(),
                snapshot.atlasUrl()
        );
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
