package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.art.ArtworkInfoResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Snapshot autenticado do pet: bloco compartilhado (CA-009) mais fila e estatísticas da conta.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountPetResponse(
        String petName,
        String presentation,
        String petState,
        String reserveHours,
        Boolean awaitingReference,
        Boolean pendingMovesEgg,
        String operationalLabel,
        String artworkVersion,
        String atlasUrl,
        ArtworkInfoResponse artwork,
        PresentationQueueResponse presentationQueue,
        PetStatsResponse stats
) {
    public static AccountPetResponse of(
            PetPublicSnapshot snapshot,
            ArtworkInfoResponse artwork,
            PresentationQueueResponse presentationQueue,
            PetStatsResponse stats
    ) {
        PetPublicSnapshot pet = snapshot == null ? PetPublicSnapshot.empty() : snapshot;
        return new AccountPetResponse(
                pet.petName(),
                pet.presentation(),
                pet.petState(),
                pet.reserveHours(),
                pet.awaitingReference(),
                pet.pendingMovesEgg(),
                pet.operationalLabel(),
                pet.artworkVersion(),
                pet.atlasUrl(),
                artwork,
                presentationQueue,
                stats
        );
    }
}
