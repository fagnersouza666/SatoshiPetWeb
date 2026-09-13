package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência da porção de referência: fonte do criador, fallback ao vínculo
 * mais antigo e última porção positiva (CC-05, CC-11, CA-026, CA-055).
 */
@ApplicationScoped
public class PersistentPetReferencePortionPort implements PetReferencePortionPort {

    @Override
    @Transactional
    public Optional<ResolvedPortion> currentPositivePortion(UUID petId) {
        Pet pet = loadPet(petId);
        refreshFoodSource(pet);
        if (pet.foodSourceAccount != null) {
            Optional<PetReferencePortion> scoped =
                    PetReferencePortion.latestForPetAndSource(pet, pet.foodSourceAccount);
            if (scoped.isPresent()) {
                PetReferencePortion snapshot = scoped.get();
                UUID sourceId = snapshot.sourceAccount == null ? null : snapshot.sourceAccount.id;
                return Optional.of(new ResolvedPortion(snapshot.portionSats, snapshot.origin, sourceId));
            }
        }
        if (pet.lastPositivePortionSats != null && pet.lastPositivePortionSats > 0L) {
            PortionOrigin origin = pet.lastPositivePortionOrigin != null
                    ? pet.lastPositivePortionOrigin
                    : PortionOrigin.CREATOR_PLAN;
            UUID sourceId = pet.foodSourceAccount == null ? null : pet.foodSourceAccount.id;
            return Optional.of(new ResolvedPortion(pet.lastPositivePortionSats, origin, sourceId));
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void recordPositivePortion(
            UUID petId,
            UUID sourceAccountId,
            long portionSats,
            PortionOrigin origin,
            Instant validFrom
    ) {
        if (portionSats <= 0L) {
            throw new IllegalArgumentException("porção deve ser maior que zero");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(validFrom, "validFrom");
        Pet pet = loadPet(petId);
        Account source = loadSourceAccount(sourceAccountId);
        PetReferencePortion.create(pet, source, portionSats, validFrom, origin, validFrom).persist();
        pet.lastPositivePortionSats = portionSats;
        pet.lastPositivePortionOrigin = origin;
        pet.awaitingReference = false;
        pet.updatedAt = validFrom;
    }

    @Override
    @Transactional
    public void refreshFoodSource(UUID petId) {
        refreshFoodSource(loadPet(petId));
    }

    private void refreshFoodSource(Pet pet) {
        boolean active = pet.foodSourceAccount != null
                && AccountAddressBinding.isActivelyBound(pet.foodSourceAccount, pet.address);
        if (active) {
            return;
        }
        pet.foodSourceAccount = AccountAddressBinding.findOldestActiveByAddress(pet.address)
                .map(binding -> binding.account)
                .orElse(null);
    }

    private static Pet loadPet(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        Pet pet = Pet.findById(petId);
        if (pet == null) {
            throw new IllegalArgumentException("Pet não encontrado: " + petId);
        }
        return pet;
    }

    private static Account loadSourceAccount(UUID sourceAccountId) {
        if (sourceAccountId == null) {
            return null;
        }
        Account source = Account.findById(sourceAccountId);
        if (source == null) {
            throw new IllegalArgumentException("Conta fonte não encontrada: " + sourceAccountId);
        }
        return source;
    }
}
