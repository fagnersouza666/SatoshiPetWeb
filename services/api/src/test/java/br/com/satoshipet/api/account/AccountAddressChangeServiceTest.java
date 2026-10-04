package br.com.satoshipet.api.account;

import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class AccountAddressChangeServiceTest {

    @Inject
    AccountAddressChangeService changeService;

    private static final String MAINNET_ADDRESS_A = BitcoinTestAddresses.MAINNET_BECH32;
    private static final String MAINNET_ADDRESS_B = BitcoinTestAddresses.MAINNET_P2PKH;

    @Test
    @Transactional
    void trocaEnderecoComSucessoDentroJanela() {
        Account account = criarConta();
        Instant now = Instant.now();
        account.addressChangeDeadline = now.plus(Duration.ofHours(72));

        // Vínculo inicial
        Address inicial = Address.create(MAINNET_ADDRESS_A, now);
        inicial.persist();
        AccountAddressBinding binding = AccountAddressBinding.create(account, inicial, true, now);
        binding.persist();

        Address novo = changeService.change(account, MAINNET_ADDRESS_B, now.plusSeconds(60));

        assertNotNull(novo);
        assertEquals(MAINNET_ADDRESS_B, novo.canonical);

        // Vínculo antigo deve estar desfeito
        Optional<AccountAddressBinding> ativo = AccountAddressBinding.findActivePrimary(account);
        org.junit.jupiter.api.Assertions.assertTrue(ativo.isPresent());
        assertEquals(MAINNET_ADDRESS_B, ativo.get().address.canonical);
        assertNotNull(br.com.satoshipet.api.pet.Pet.findByAddress(novo).orElse(null),
                "A troca para um endereço novo deve criar seu pet na mesma transação");
    }

    @Test
    @Transactional
    void rejeitaTrocaForaDaJanela72h() {
        Instant now = Instant.now();
        Account account = criarContaComDeadline(now.minusSeconds(1));

        AccountAddressChangeService.AddressChangeException ex = assertThrows(
                AccountAddressChangeService.AddressChangeException.class,
                () -> changeService.change(account, MAINNET_ADDRESS_B, now)
        );
        assertEquals("window_expired", ex.getCode());
    }

    @Test
    @Transactional
    void rejeitaEnderecoInvalido() {
        Account account = criarConta();
        account.addressChangeDeadline = Instant.now().plus(Duration.ofHours(72));

        AccountAddressChangeService.AddressChangeException ex = assertThrows(
                AccountAddressChangeService.AddressChangeException.class,
                () -> changeService.change(account, "endereco-invalido", Instant.now())
        );
        assertEquals("invalid_address", ex.getCode());
    }

    @Test
    @Transactional
    void rejeitaQuandoDeadlineNulo() {
        Account account = criarContaComDeadline(null);

        AccountAddressChangeService.AddressChangeException ex = assertThrows(
                AccountAddressChangeService.AddressChangeException.class,
                () -> changeService.change(account, MAINNET_ADDRESS_B, Instant.now())
        );
        assertEquals("window_expired", ex.getCode());
    }

    private Account criarConta() {
        return criarContaComDeadline(Instant.now().plus(Duration.ofHours(72)));
    }

    private Account criarContaComDeadline(Instant deadline) {
        Account account = Account.create(
                "addr-change-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo", "pt-BR", Instant.now()
        );
        account.addressChangeDeadline = deadline;
        account.persist();
        return account;
    }
}
