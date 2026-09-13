package br.com.satoshipet.api.account;

import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AccountAddressModelTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void criaContaComPrazoDeTrocaDe72HorasEImutavel() {
        Instant createdAt = Instant.parse("2026-01-01T12:00:00Z");
        Account account = Account.create(
                "account-model-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo", "pt-BR", createdAt
        );
        account.persist();
        entityManager.flush();

        assertEquals(createdAt.plus(Duration.ofHours(72)), account.addressChangeDeadline);

        account.addressChangeDeadline = createdAt.plus(Duration.ofDays(30));
        entityManager.flush();
        entityManager.clear();

        Account persisted = Account.findById(account.id);
        assertNotNull(persisted);
        assertEquals(createdAt.plus(Duration.ofHours(72)), persisted.addressChangeDeadline);
    }

    @Test
    @Transactional
    void identificaRedeEUsaRedeNaBuscaDoEndereco() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        Address mainnet = findOrCreateAddress(BitcoinTestAddresses.MAINNET_BECH32, now);
        Address regtest = findOrCreateAddress(BitcoinTestAddresses.REGTEST_BECH32, now);
        entityManager.flush();

        assertEquals("mainnet", mainnet.network);
        assertEquals("regtest", regtest.network);
        assertTrue(Address.findByNetworkAndCanonical("mainnet", mainnet.canonical).isPresent());
        assertTrue(Address.findByNetworkAndCanonical("regtest", regtest.canonical).isPresent());
    }

    @Test
    @Transactional
    void permiteMesmoEnderecoParaContasDiferentes() {
        Instant now = Instant.now();
        Account first = createAccount(now);
        Account second = createAccount(now);
        Address address = findOrCreateAddress(BitcoinTestAddresses.MAINNET_P2PKH, now);

        AccountAddressBinding.create(first, address, true, now).persist();
        AccountAddressBinding.create(second, address, true, now).persist();
        entityManager.flush();

        assertEquals(1, AccountAddressBinding.findByAccount(first).size());
        assertEquals(1, AccountAddressBinding.findByAccount(second).size());
    }

    @Test
    @Transactional
    void impedeDoisVinculosAtivosParaMesmaConta() {
        Instant now = Instant.now();
        Account account = createAccount(now);
        Address firstAddress = findOrCreateAddress(BitcoinTestAddresses.MAINNET_BECH32, now);
        Address secondAddress = findOrCreateAddress(BitcoinTestAddresses.MAINNET_P2PKH, now);
        AccountAddressBinding.create(account, firstAddress, true, now).persist();
        entityManager.flush();

        AccountAddressBinding.create(account, secondAddress, true, now).persist();
        assertThrows(PersistenceException.class, () -> entityManager.flush());
    }

    @Test
    @Transactional
    void impedeFimDoVinculoAntesDoInicio() {
        Instant boundAt = Instant.parse("2026-01-01T12:00:00Z");
        AccountAddressBinding binding = AccountAddressBinding.create(
                createAccount(boundAt),
                Address.create(BitcoinTestAddresses.MAINNET_BECH32, boundAt),
                true,
                boundAt
        );

        assertNull(binding.unboundAt);
        assertThrows(IllegalArgumentException.class,
                () -> binding.unbind(boundAt.minusSeconds(1)));
        assertNull(binding.unboundAt);
    }

    @Test
    @Transactional
    void permiteNovoVinculoDepoisDeEncerrarOAnterior() {
        Instant boundAt = Instant.parse("2026-01-01T12:00:00Z");
        Instant unboundAt = boundAt.plusSeconds(1);
        Account account = createAccount(boundAt);
        Address firstAddress = findOrCreateAddress(BitcoinTestAddresses.MAINNET_BECH32, boundAt);
        Address secondAddress = findOrCreateAddress(BitcoinTestAddresses.MAINNET_P2PKH, boundAt);
        AccountAddressBinding first = AccountAddressBinding.create(account, firstAddress, true, boundAt);
        first.persist();
        entityManager.flush();

        first.unbind(unboundAt);
        entityManager.flush();
        AccountAddressBinding.create(account, secondAddress, true, unboundAt).persist();
        entityManager.flush();

        assertTrue(AccountAddressBinding.findActivePrimary(account)
                .map(binding -> binding.address.id.equals(secondAddress.id))
                .orElse(false));
    }

    private Account createAccount(Instant now) {
        Account account = Account.create(
                "account-model-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo", "pt-BR", now
        );
        account.persist();
        return account;
    }

    private Address findOrCreateAddress(String canonical, Instant now) {
        String network = canonical.startsWith("bcrt1") ? "regtest" : "mainnet";
        return Address.findByNetworkAndCanonical(network, canonical).orElseGet(() -> {
            Address address = Address.create(canonical, network, now);
            address.persist();
            return address;
        });
    }
}
