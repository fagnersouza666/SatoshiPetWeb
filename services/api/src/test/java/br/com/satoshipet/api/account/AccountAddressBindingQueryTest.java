package br.com.satoshipet.api.account;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consultas de vínculo ativo por endereço (fonte alimentar CC-05). */
@QuarkusTest
class AccountAddressBindingQueryTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");

    @Test
    @Transactional
    void findActiveByAddressIgnoraVinculosDesfeitos() {
        Address address = persistAddress("active");
        Account kept = persistAccount("kept");
        Account unbound = persistAccount("unbound");
        AccountAddressBinding.create(kept, address, true, NOW).persist();
        AccountAddressBinding leaving = AccountAddressBinding.create(
                unbound, address, true, NOW.plusSeconds(1));
        leaving.persist();
        leaving.unbind(NOW.plusSeconds(10));

        List<AccountAddressBinding> active = AccountAddressBinding.findActiveByAddress(address);

        assertEquals(1, active.size());
        assertEquals(kept.id, active.get(0).account.id);
        assertTrue(active.get(0).unboundAt == null);
    }

    @Test
    @Transactional
    void findOldestActiveByAddressOrdenaPorBoundAtDepoisId() {
        Address address = persistAddress("oldest");
        Account later = persistAccount("later");
        Account earlier = persistAccount("earlier");
        AccountAddressBinding.create(later, address, true, NOW.plusSeconds(60)).persist();
        AccountAddressBinding.create(earlier, address, true, NOW).persist();

        Optional<AccountAddressBinding> oldest =
                AccountAddressBinding.findOldestActiveByAddress(address);

        assertTrue(oldest.isPresent());
        assertEquals(earlier.id, oldest.get().account.id);
        assertEquals(NOW, oldest.get().boundAt);
    }

    @Test
    @Transactional
    void findOldestActiveByAddressDesempataPorIdQuandoBoundAtIgual() {
        Address address = persistAddress("tie");
        Account first = persistAccount("tie-a");
        Account second = persistAccount("tie-b");
        AccountAddressBinding smallerId = AccountAddressBinding.create(first, address, true, NOW);
        smallerId.id = UUID.fromString("00000000-0000-4000-8000-000000000001");
        smallerId.persist();
        AccountAddressBinding largerId = AccountAddressBinding.create(second, address, true, NOW);
        largerId.id = UUID.fromString("00000000-0000-4000-8000-000000000002");
        largerId.persist();

        Optional<AccountAddressBinding> oldest =
                AccountAddressBinding.findOldestActiveByAddress(address);

        assertTrue(oldest.isPresent());
        assertEquals(smallerId.id, oldest.get().id);
        assertEquals(2, AccountAddressBinding.findActiveByAddress(address).size());
        assertEquals(largerId.address.id, address.id);
    }

    @Test
    @Transactional
    void isActivelyBoundRefleteUnboundAt() {
        Address address = persistAddress("bound");
        Account account = persistAccount("bound");
        AccountAddressBinding binding = AccountAddressBinding.create(account, address, true, NOW);
        binding.persist();

        assertTrue(AccountAddressBinding.isActivelyBound(account, address));

        binding.unbind(NOW.plusSeconds(5));

        assertFalse(AccountAddressBinding.isActivelyBound(account, address));
    }

    private static Address persistAddress(String marker) {
        Address address = Address.create(uniqueCanonical(marker), NOW);
        address.persist();
        return address;
    }

    private static Account persistAccount(String marker) {
        Account account = Account.create(
                marker + "-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                NOW
        );
        account.persist();
        return account;
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
