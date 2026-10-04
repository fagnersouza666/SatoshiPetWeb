package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.LogicalReceipt;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PetConcurrencyRegressionTest {
    @Inject PetLifecyclePort lifecycle;
    @Inject PetReferencePortionPort portions;

    @Test
    void tickRecarregaEstadoAposRecebimentoEmOutraTransacao() throws Exception {
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        UUID[] ids = QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.create(UUID.randomUUID()+"@test.local", "UTC", "pt-BR", now);
            account.persist();
            Address address = Address.create("bcrt1q"+UUID.randomUUID().toString().replace("-", ""), now);
            address.persist();
            AccountAddressBinding.create(account, address, true, now).persist();
            Pet pet = Pet.create(address, account, "Teste", now);
            pet.presentation = PetPresentation.CREATURE;
            pet.persist();
            portions.recordPositivePortion(pet.id, account.id, 20_000L, PortionOrigin.CREATOR_PLAN, now);
            LogicalReceipt receipt = LogicalReceipt.createPending(address, UUID.randomUUID().toString(), 20_000L, now);
            receipt.persist();
            return new UUID[]{pet.id, receipt.id};
        });
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        CompletableFuture<Void> credited = new CompletableFuture<>();
        CompletableFuture<Void> tick = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew().run(() -> {
            Pet.findById(ids[0]); // Simula entidade carregada pelo job antes da trava de escrita.
            loaded.complete(null);
            credited.orTimeout(10, TimeUnit.SECONDS).join();
            lifecycle.tick(ids[0], now.plusSeconds(3600));
        }));
        try {
            loaded.get(10, TimeUnit.SECONDS);
            QuarkusTransaction.requiringNew().run(() ->
                    lifecycle.onReceiptObserved(ids[0], ids[1], 20_000L, true, now));
        } finally {
            credited.complete(null);
        }
        tick.get(10, TimeUnit.SECONDS);
        BigDecimal reserve = QuarkusTransaction.requiringNew().call(() -> ((Pet) Pet.findById(ids[0])).reserveHours);
        assertEquals(0, reserve.compareTo(new BigDecimal("23")));
    }
}
