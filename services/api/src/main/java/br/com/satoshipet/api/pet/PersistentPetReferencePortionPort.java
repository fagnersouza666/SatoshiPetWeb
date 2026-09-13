package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
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

    private final Instance<PetLifecyclePort> lifecycle;

    @Inject
    public PersistentPetReferencePortionPort(Instance<PetLifecyclePort> lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    @Transactional
    public Optional<ResolvedPortion> currentPositivePortion(UUID petId) {
        Pet pet = loadPet(petId);
        Account previousFood = pet.foodSourceAccount;
        refreshFoodSource(pet);
        Account food = pet.foodSourceAccount;
        if (food != null) {
            Optional<PetReferencePortion> scoped =
                    PetReferencePortion.latestForPetAndSource(pet, food);
            if (scoped.isPresent()) {
                return Optional.of(toResolved(scoped.get()));
            }
            return fallbackLastPositive(pet, food.id, previousFood);
        }
        return fallbackLastPositive(pet, null, previousFood);
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
        refreshFoodSource(pet);
        boolean wasAwaitingReference = pet.awaitingReference;
        if (sameAccount(source, pet.foodSourceAccount)) {
            pet.lastPositivePortionSats = portionSats;
            pet.lastPositivePortionOrigin = origin;
            pet.awaitingReference = false;
            pet.updatedAt = validFrom;
        }
        if (wasAwaitingReference && !pet.awaitingReference) {
            lifecycle.get().reconstruct(petId, validFrom);
        }
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

    private Optional<ResolvedPortion> fallbackLastPositive(
            Pet pet, UUID currentFoodId, Account previousFood
    ) {
        if (pet.lastPositivePortionSats == null || pet.lastPositivePortionSats <= 0L) {
            return Optional.empty();
        }
        UUID ownerId = lastPositiveSourceAccountId(pet);
        if (ownerId == null && previousFood != null && !sameAccount(previousFood, pet.foodSourceAccount)) {
            ownerId = previousFood.id;
        }
        if (currentFoodId != null && (ownerId == null || ownerId.equals(currentFoodId))) {
            return Optional.empty();
        }
        PortionOrigin origin = pet.lastPositivePortionOrigin != null
                ? pet.lastPositivePortionOrigin
                : PortionOrigin.CREATOR_PLAN;
        return Optional.of(new ResolvedPortion(pet.lastPositivePortionSats, origin, ownerId));
    }

    private static UUID lastPositiveSourceAccountId(Pet pet) {
        return PetReferencePortion.latestMatchingLastPositive(
                        pet, pet.lastPositivePortionSats, pet.lastPositivePortionOrigin)
                .map(snapshot -> snapshot.sourceAccount == null ? null : snapshot.sourceAccount.id)
                .orElse(null);
    }

    private static ResolvedPortion toResolved(PetReferencePortion snapshot) {
        UUID sourceId = snapshot.sourceAccount == null ? null : snapshot.sourceAccount.id;
        return new ResolvedPortion(snapshot.portionSats, snapshot.origin, sourceId);
    }

    private static boolean sameAccount(Account left, Account right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.id.equals(right.id);
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
