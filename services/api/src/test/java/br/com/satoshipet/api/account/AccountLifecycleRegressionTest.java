package br.com.satoshipet.api.account;

import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetReferencePortion;
import br.com.satoshipet.api.pet.PortionOrigin;
import br.com.satoshipet.api.platform.AuthenticatedSession;
import br.com.satoshipet.api.platform.SessionAuthFilter;
import br.com.satoshipet.api.auth.LogoutResource;
import br.com.satoshipet.api.isolation.AccountPrivateDataWipePort;
import br.com.satoshipet.api.isolation.AccountDeletionTombstone;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/** Requests independentes: nenhuma transação externa pode mascarar ausência de commit. */
@QuarkusTest
class AccountLifecycleRegressionTest {
    @Inject SessionService sessions;
    @Inject RecoveryService recovery;
    @Inject MagicLinkTokenService links;
    @Inject AccountResource resource;
    @Inject AuthenticatedSession authenticated;
    @Inject SessionAuthFilter sessionFilter;
    @Inject LogoutResource logout;
    @Inject AccountPrivateDataWipePort wipe;

    @Test
    void bootstrapRecuperaMesmoCsrfEmDuasAbasEPermiteLogout() {
        Fixture f = fixture();
        ContainerRequestContext request = (ContainerRequestContext) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (proxy, method, args) -> method.getName().equals("getCookies")
                        ? Map.of("sp_session", new Cookie("sp_session", f.session.rawSessionToken())) : null);
        sessionFilter.filter(request);
        var firstResponse = resource.me();
        assertEquals("no-store", firstResponse.getHeaderString("Cache-Control"));
        String first = firstResponse.getHeaderString("X-CSRF-Token");
        authenticated.set(null);
        sessionFilter.filter(request);
        String second = resource.me().getHeaderString("X-CSRF-Token");
        assertEquals(f.session.rawCsrfToken(), first);
        assertEquals(first, second);
        assertEquals(204, logout.logout(request).getStatus());
        assertTrue(sessions.findActive(f.session.rawSessionToken(), Instant.now()).isEmpty());
    }

    @Test
    void renamePersisteEntreRequests() {
        Fixture f = fixture();
        authenticated.set(f.session.session());
        assertEquals(200, resource.renamePet(new AccountResource.PetNameRequest("Nome atualizado")).getStatus());
        QuarkusTransaction.requiringNew().run(() -> {
            Pet.getEntityManager().clear();
            assertEquals("Nome atualizado", ((Pet) Pet.findById(f.petId)).name);
        });
    }

    @Test
    void exclusaoRemovePrivadosEPreservaPetSnapshotERede() {
        Fixture f = fixture();
        String rawRecovery = QuarkusTransaction.requiringNew().call(() ->
                recovery.generate(Account.findById(f.accountId), Instant.now()));
        String oldLink = "delete-old-link-" + UUID.randomUUID();
        links.issue(f.email, oldLink, Instant.now());
        authenticated.set(f.session.session());
        assertEquals(204, resource.deleteAccount(null).getStatus());

        QuarkusTransaction.requiringNew().run(() -> {
            assertNull(Account.findById(f.accountId));
            assertEquals(0, Session.count("account.id", f.accountId));
            assertEquals(0, RecoveryCode.count("account.id", f.accountId));
            assertEquals(0, AccountAddressBinding.count("account.id", f.accountId));
            Pet pet = Pet.findById(f.petId);
            assertNotNull(pet);
            assertNull(pet.creatorAccount);
            assertNull(pet.foodSourceAccount);
            assertEquals("Pet preservado", pet.name);
            assertEquals(2500L, pet.lastPositivePortionSats);
            PetReferencePortion portion = PetReferencePortion.find("pet.id", f.petId).firstResult();
            assertEquals(2500L, portion.portionSats);
            assertNull(portion.sourceAccount);
            assertNotNull(Address.findById(f.addressId));
        });
        assertTrue(links.peekByRawToken(oldLink, Instant.now()).isEmpty());
        assertTrue(sessions.findActive(f.session.rawSessionToken(), Instant.now()).isEmpty());
        assertNotNull(AccountDeletionTombstone.findById(f.accountId));
        wipe.wipe(f.accountId);
        assertEquals(1, AccountDeletionTombstone.count("accountId", f.accountId));
    }

    @Test
    void exclusaoNaoRemoveOutrasContasNoMesmoEndereco() {
        Fixture f = fixture();
        UUID otherId = QuarkusTransaction.requiringNew().call(() -> {
            Account other = Account.create("other-" + UUID.randomUUID() + "@test.invalid",
                    "America/Sao_Paulo", "pt-BR", Instant.now());
            other.persist();
            AccountAddressBinding.create(other, Address.findById(f.addressId), true, Instant.now()).persist();
            return other.id;
        });
        wipe.wipe(f.accountId);
        QuarkusTransaction.requiringNew().run(() -> {
            assertNotNull(Account.findById(otherId));
            assertEquals(1, AccountAddressBinding.count("account.id", otherId));
            assertNotNull(Pet.findById(f.petId));
        });
    }

    private Fixture fixture() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Instant now = Instant.now();
            Account account = Account.create("audit-" + UUID.randomUUID() + "@test.invalid",
                    "America/Sao_Paulo", "pt-BR", now);
            account.persist();
            Address address = Address.create("bcrt1audit" + UUID.randomUUID(), "regtest", now);
            address.persist();
            AccountAddressBinding.create(account, address, true, now).persist();
            Pet pet = Pet.create(address, account, "Pet preservado", now);
            pet.lastPositivePortionSats = 2500L;
            pet.persist();
            PetReferencePortion.create(pet, account, 2500L, now, PortionOrigin.CREATOR_PLAN, now).persist();
            return new Fixture(account.id, account.email, pet.id, address.id,
                    sessions.create(account, now, "audit", "127.0.0.1"));
        });
    }

    record Fixture(UUID accountId, String email, UUID petId, UUID addressId,
                   SessionService.SessionCreation session) {}
}
