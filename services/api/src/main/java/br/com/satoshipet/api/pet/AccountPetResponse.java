package br.com.satoshipet.api.pet;

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
        PresentationQueueResponse presentationQueue,
        PetStatsResponse stats
) {
    public static AccountPetResponse of(
            PetPublicSnapshot snapshot,
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
                presentationQueue,
                stats
        );
    }
}
