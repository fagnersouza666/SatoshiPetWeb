package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.LogicalReceipt;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import java.util.Optional;

/**
 * Bloco compartilhado do pet (CA-009): sem {@code petId}, {@code accountId} ou e-mail.
 *
 * <p>Usado na página pública e em {@code GET /api/v1/account/pet}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PetPublicSnapshot(
        String petName,
        String presentation,
        String petState,
        String reserveHours,
        Boolean awaitingReference,
        Boolean pendingMovesEgg,
        String operationalLabel
) {

    static final String LABEL_AWAITING_REFERENCE = "Aguardando referência do plano";
    static final String LABEL_PENDING_RECEIPT = "Recebimento pendente";
    static final String LABEL_PREPARING_BIRTH = "Preparando nascimento";

    public static PetPublicSnapshot empty() {
        return new PetPublicSnapshot(null, null, null, null, null, null, null);
    }

    public static PetPublicSnapshot from(Pet pet, long pendingIncomingSats) {
        Objects.requireNonNull(pet, "pet");
        boolean egg = pet.presentation == PetPresentation.EGG;
        boolean pendingMovesEgg = egg && pendingIncomingSats > 0L;
        String petState = egg || pet.emotionalState == null ? null : pet.emotionalState.name();
        return new PetPublicSnapshot(
                pet.name,
                pet.presentation == null ? null : pet.presentation.name(),
                petState,
                pet.reserveHours == null ? null : pet.reserveHours.toPlainString(),
                pet.awaitingReference,
                pendingMovesEgg,
                operationalLabel(pet, pendingMovesEgg)
        );
    }

    /**
     * Carrega o pet do endereço e calcula sats pendentes de entrada.
     *
     * @return snapshot preenchido, ou {@link #empty()} se não houver pet
     */
    public static PetPublicSnapshot fromAddress(Address address) {
        Objects.requireNonNull(address, "address");
        Optional<Pet> pet = Pet.findByAddress(address);
        if (pet.isEmpty()) {
            return empty();
        }
        long pendingIncomingSats = LogicalReceipt.findByAddress(address).stream()
                .mapToLong(receipt -> receipt.pendingSats)
                .sum();
        return from(pet.get(), pendingIncomingSats);
    }

    private static String operationalLabel(Pet pet, boolean pendingMovesEgg) {
        if (pet.awaitingReference) {
            return LABEL_AWAITING_REFERENCE;
        }
        if (pendingMovesEgg) {
            return LABEL_PENDING_RECEIPT;
        }
        if (pet.presentation == PetPresentation.EGG
                && pet.bornAt != null
                && pet.artworkStatus != ArtworkStatus.APPROVED) {
            return LABEL_PREPARING_BIRTH;
        }
        return null;
    }
}
