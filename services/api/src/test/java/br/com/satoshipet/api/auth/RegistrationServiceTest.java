package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenHasher;
import br.com.satoshipet.api.account.MagicLinkTokenRepository;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class RegistrationServiceTest {

    @Inject
    RegistrationService registrationService;

    @Inject
    MagicLinkTokenRepository tokenRepository;

    @Inject
    MagicLinkTokenHasher tokenHasher;

    @ParameterizedTest
    @ValueSource(strings = {"America/Sao Paulo", "-03:00", "UTC+03:00", "X", "America/Inexistente"})
    void rejeitaFusoForaDoCatalogoIana(String timezone) {
        String raw = "tz-" + UUID.randomUUID();
        Instant now = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> tokenRepository.persist(MagicLinkToken.issue(
                raw + "@test.invalid", tokenHasher.hash(raw), now, now.plusSeconds(900))));
        var error = assertThrows(RegistrationService.RegistrationException.class, () ->
                registrationService.register(raw, BitcoinTestAddresses.MAINNET_BECH32,
                        "Pixel", timezone, null, null, Instant.now()));
        assertEquals("invalid_timezone", error.getCode());
    }

    @Test
    void reutilizaPetExistenteQuandoEnderecoJaMonitorado() {
        Instant now = Instant.now();
        String canonical = BitcoinTestAddresses.MAINNET_WITH_PET;
        String email = "second-" + UUID.randomUUID() + "@test.com";
        String rawToken = "reg-shared-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            Address address = Address.create(canonical, now);
            address.persist();
            Account firstAccount = Account.create(
                    "first-" + UUID.randomUUID() + "@test.com",
                    "America/Sao_Paulo", "pt-BR", now);
            firstAccount.persist();
            Pet existing = Pet.create(address, firstAccount, "PetOriginal", now);
            existing.persist();
            MagicLinkToken token = MagicLinkToken.issue(
                    email,
                    tokenHasher.hash(rawToken),
                    now,
                    now.plusSeconds(900)
            );
            tokenRepository.persist(token);
        });

        registrationService.register(
                rawToken,
                canonical,
                "OutroNome",
                "America/Sao_Paulo",
                "test-agent",
                "127.0.0.1",
                Instant.now()
        );

        QuarkusTransaction.requiringNew().run(() -> {
            Address address = Address.findByCanonical(canonical).orElseThrow();
            assertEquals(1, Pet.find("address", address).count());
            Pet pet = Pet.findByAddress(address).orElseThrow();
            assertEquals("PetOriginal", pet.name);
            assertNotNull(Account.findByEmail(email));
        });
    }
}
