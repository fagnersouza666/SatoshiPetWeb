package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.Pet;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ArtworkGenerationConsumerTest {

    private static final Instant NOW = Instant.parse("2026-09-14T15:00:00Z");

    @Inject
    ArtworkGenerationConsumer consumer;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void consumePetBornEnfileiraArtworkIdempotente() throws Exception {
        UUID petId = persistBornPet("consumer");
        OutboxEvent event = buildPetBornEvent(petId);

        QuarkusTransaction.requiringNew().run(() -> consumer.consume(event));
        QuarkusTransaction.requiringNew().run(() -> consumer.consume(event));

        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            assertEquals(1, PetArtwork.count("pet", pet));
        });
    }

    @Test
    void supportsSomentePetBorn() {
        assertTrue(consumer.supports("PET_BORN"));
    }

    private OutboxEvent buildPetBornEvent(UUID petId) throws Exception {
        String canonical = QuarkusTransaction.requiringNew().call(() -> {
            Pet pet = Pet.findById(petId);
            return pet.address.canonical;
        });
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "address", canonical,
                "eventType", "PET_BORN",
                "occurredAt", NOW.toString(),
                "presentation", "EGG"
        ));
        return OutboxEvent.createWithId(
                UUID.randomUUID(),
                "Pet",
                petId.toString(),
                "PET_BORN",
                payload,
                NOW,
                null
        );
    }

    private static UUID persistBornPet(String marker) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Address address = Address.create(uniqueCanonical(marker), NOW);
            address.persist();
            Account account = Account.create(
                    marker + "-" + UUID.randomUUID() + "@test.com",
                    "America/Sao_Paulo",
                    "pt-BR",
                    NOW
            );
            account.persist();
            AccountAddressBinding binding = AccountAddressBinding.create(account, address, true, NOW);
            binding.persist();
            Pet pet = Pet.create(address, account, "Arte-" + marker, NOW);
            pet.bornAt = NOW;
            pet.artworkStatus = ArtworkStatus.PENDING;
            pet.persist();
            return pet.id;
        });
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
