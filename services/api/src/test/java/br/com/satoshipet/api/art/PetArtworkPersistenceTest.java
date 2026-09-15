package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.Pet;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistência V7: PetArtwork e tentativas (ART-01). */
@QuarkusTest
class PetArtworkPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Inject
    Flyway flyway;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void flywayCorrenteEhVersao7() {
        assertEquals("7", flyway.info().current().getVersion().getVersion());
    }

    @Test
    void persisteArtworkComContextoCongeladoEPromptPrivado() {
        UUID artworkId = QuarkusTransaction.requiringNew().call(() -> {
            Pet pet = persistPet("art-1");
            FrozenGenerationContext context = new FrozenGenerationContext(
                    pet.address.canonical,
                    "America/Sao_Paulo",
                    "desconhecida",
                    "indisponivel",
                    "day",
                    NOW.toString()
            );
            PetArtwork artwork = PetArtwork.createPending(pet, context, "prompt-secreto-privado", NOW);
            artwork.persist();
            return artwork.id;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork reloaded = PetArtwork.findById(artworkId);
            assertNotNull(reloaded);
            assertEquals(ArtGenerationStatus.GENERATING, reloaded.generationStatus);
            assertEquals("prompt-secreto-privado", reloaded.promptPrivate);
            assertEquals("stub-v1", reloaded.modelId);
            assertEquals(0, reloaded.assetVersion);
            assertFalse(reloaded.voluntaryRegenUsed);

            FrozenGenerationContext ctx = reloaded.frozenContext();
            assertEquals("desconhecida", ctx.locationLabel());
            assertEquals("indisponivel", ctx.weatherSummary());
        });
    }

    @Test
    void segundoArtworkParaMesmoPetViolaUnicidade() {
        UUID petId = QuarkusTransaction.requiringNew().call(() -> {
            Pet pet = persistPet("dup-art");
            PetArtwork artwork = PetArtwork.createPending(
                    pet,
                    sampleContext(pet),
                    "prompt-a",
                    NOW
            );
            artwork.persist();
            return pet.id;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    Pet pet = Pet.findById(petId);
                    PetArtwork.createPending(pet, sampleContext(pet), "prompt-b", NOW).persist();
                    PetArtwork.flush();
                }));
        assertTrue(containsPersistenceException(thrown));
    }

    @Test
    void tentativasSaoUnicasPorNumero() {
        UUID artworkId = QuarkusTransaction.requiringNew().call(() -> {
            Pet pet = persistPet("attempts");
            PetArtwork artwork = PetArtwork.createPending(pet, sampleContext(pet), "prompt", NOW);
            artwork.persist();
            PetArtworkAttempt.create(
                    artwork,
                    1,
                    ArtAttemptReason.INITIAL,
                    true,
                    null,
                    "pets/" + pet.id + "/v1/staging",
                    NOW
            ).persist();
            return artwork.id;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    PetArtwork artwork = PetArtwork.findById(artworkId);
                    PetArtworkAttempt.create(
                            artwork,
                            1,
                            ArtAttemptReason.TECHNICAL_RETRY,
                            false,
                            "invalid",
                            "pets/x/v1/staging",
                            NOW
                    ).persist();
                    PetArtworkAttempt.flush();
                }));
        assertTrue(containsPersistenceException(thrown));

        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findById(artworkId);
            assertEquals(1, PetArtworkAttempt.listByArtwork(artwork).size());
            assertEquals(2, PetArtworkAttempt.nextAttemptNo(artwork));
        });
    }

    private static Pet persistPet(String marker) {
        Address address = Address.create(uniqueCanonical(marker), NOW);
        address.persist();
        Account account = Account.create(
                marker + "-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                NOW
        );
        account.persist();
        Pet pet = Pet.create(address, account, "Arte-" + marker, NOW);
        pet.persist();
        return pet;
    }

    private static FrozenGenerationContext sampleContext(Pet pet) {
        return new FrozenGenerationContext(
                pet.address.canonical,
                "America/Sao_Paulo",
                "desconhecida",
                "indisponivel",
                "day",
                NOW.toString()
        );
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean containsPersistenceException(Throwable thrown) {
        Throwable current = thrown;
        while (current != null) {
            if (current instanceof PersistenceException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
