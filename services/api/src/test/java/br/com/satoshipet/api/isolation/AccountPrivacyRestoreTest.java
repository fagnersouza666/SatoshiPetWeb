package br.com.satoshipet.api.isolation;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.account.Session;
import br.com.satoshipet.api.account.SessionService;
import br.com.satoshipet.api.pet.Pet;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AccountPrivacyRestoreTest {
    @Inject DatabaseAccountPrivateDataWipePort wipe;
    @Inject SessionService sessions;

    @Test
    void reaplicaDiarioImportadoAntesDeLiberarContaRestaurada() {
        UUID[] ids = QuarkusTransaction.requiringNew().call(() -> {
            Instant now = Instant.now();
            Account account = Account.create("restore-" + UUID.randomUUID() + "@test.invalid",
                    "America/Sao_Paulo", "pt-BR", now);
            account.persist();
            Address address = Address.create("bcrt1qrestore" + UUID.randomUUID().toString().replace("-", ""), now);
            address.persist();
            Pet pet = Pet.create(address, account, "Preservado", now);
            pet.persist();
            sessions.create(account, now, null, null);
            AccountDeletionTombstone deletion = new AccountDeletionTombstone();
            deletion.accountId = account.id;
            deletion.deletedAt = now;
            deletion.persist();
            return new UUID[]{account.id, pet.id};
        });

        wipe.restorePrivacy(null);
        wipe.restorePrivacy(null);

        QuarkusTransaction.requiringNew().run(() -> {
            assertNull(Account.findById(ids[0]));
            assertEquals(0, Session.count("account.id", ids[0]));
            Pet pet = Pet.findById(ids[1]);
            assertNotNull(pet);
            assertEquals("Preservado", pet.name);
            assertNull(pet.creatorAccount);
            assertNotNull(AccountDeletionTombstone.findById(ids[0]));
        });
    }
}
