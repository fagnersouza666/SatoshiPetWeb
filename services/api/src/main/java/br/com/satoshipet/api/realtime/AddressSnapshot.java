package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.pet.PetPublicSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Estado público mínimo enviado no snapshot do canal de um endereço.
 *
 * <p>Allowlist: endereço, apresentação do pet, estado emocional da criatura
 * (omitido no ovo) e origem opcional da retomada. Sem petId/accountId/e-mail.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressSnapshot(
        String address,
        String state,
        String presentation,
        String resumedFrom
) {

    public AddressSnapshot {
        Objects.requireNonNull(address, "address não pode ser nulo");
    }

    public static AddressSnapshot initial(String address, String state) {
        return new AddressSnapshot(address, state, null, null);
    }

    public static AddressSnapshot resumed(String address, String state, String resumedFrom) {
        return new AddressSnapshot(address, state, null, resumedFrom);
    }

    public static AddressSnapshot fromPet(String address, PetPublicSnapshot pet, String resumedFrom) {
        if (pet == null || pet.presentation() == null) {
            return new AddressSnapshot(address, null, null, resumedFrom);
        }
        return new AddressSnapshot(address, pet.petState(), pet.presentation(), resumedFrom);
    }
}
