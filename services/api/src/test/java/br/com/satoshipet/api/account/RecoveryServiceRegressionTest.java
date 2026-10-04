package br.com.satoshipet.api.account;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RecoveryServiceRegressionTest {
    @Inject RecoveryService recovery;
    @Inject SessionService sessions;

    @Test
    void naoConsomeCodigoNemAbreSessaoSemVerificacaoDoNovoEmail() {
        UUID[] accountId = new UUID[1];
        String code = QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.create("recovery-" + UUID.randomUUID() + "@test.invalid",
                    "America/Sao_Paulo", "pt-BR", Instant.now());
            account.persist();
            accountId[0] = account.id;
            return recovery.generate(account, Instant.now());
        });
        assertThrows(RecoveryService.RecoveryException.class,
                () -> recovery.recover(code, Instant.now(), null, null));
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(0, Session.count("account.id", accountId[0]));
            assertEquals(1, RecoveryCode.count("account.id = ?1 AND usedAt IS NULL", accountId[0]));
        });
    }

    @Test
    void trocaEmailSomenteDepoisDeVerificarMantendoIdPrazoEEmitindoNovoCodigo() {
        Fixture f = fixture();
        String email = "new-" + UUID.randomUUID() + "@test.invalid";
        var verification = recovery.requestEmail(f.code, email, Instant.now());
        QuarkusTransaction.requiringNew().run(() -> {
            assertNotEquals(email, ((Account) Account.findById(f.id)).email);
            assertTrue(sessions.findActive(f.sessionToken, Instant.now()).isPresent());
        });
        var result = recovery.recover(f.code, verification.token(), Instant.now(), null, null);
        assertNotNull(result.recoveryCode());
        assertNotEquals(f.code, result.recoveryCode());
        QuarkusTransaction.requiringNew().run(() -> {
            Account account = Account.findById(f.id);
            assertEquals(email, account.email);
            assertEquals(f.deadline, account.addressChangeDeadline);
            assertTrue(sessions.findActive(f.sessionToken, Instant.now()).isEmpty());
            assertEquals(1, RecoveryCode.count("account.id = ?1 AND usedAt IS NULL", f.id));
        });
        assertThrows(RecoveryService.RecoveryException.class,
                () -> recovery.recover(f.code, verification.token(), Instant.now(), null, null));
        assertNotNull(recovery.requestEmail(result.recoveryCode(), email, Instant.now()));
    }

    @Test
    void tokenDeOutraContaEExpiradoNaoConsomeCodigoValido() {
        Fixture first = fixture();
        Fixture second = fixture();
        var verification = recovery.requestEmail(first.code, "target-" + UUID.randomUUID() + "@test.invalid", Instant.now());
        assertThrows(RecoveryService.RecoveryException.class,
                () -> recovery.recover(second.code, verification.token(), Instant.now(), null, null));
        assertThrows(RecoveryService.RecoveryException.class,
                () -> recovery.recover(first.code, verification.token(), Instant.now().plusSeconds(3600), null, null));
        assertNotNull(recovery.recover(first.code, verification.token(), Instant.now(), null, null));
    }

    @Test
    void consumoConcorrenteCriaUmaUnicaSessao() throws Exception {
        Fixture f = fixture();
        var verification = recovery.requestEmail(f.code, "race-" + UUID.randomUUID() + "@test.invalid", Instant.now());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> task = () -> {
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                try {
                    recovery.recover(f.code, verification.token(), Instant.now(), null, null);
                    return true;
                } catch (RecoveryService.RecoveryException expected) { return false; }
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            boolean a = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            boolean b = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(a, b, "Somente um pedido pode consumir o código");
        }
        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(1, Session.count("account.id = ?1 AND invalidatedAt IS NULL", f.id)));
    }

    @Test
    void naoSubstituiEmailDeContaJaExistente() {
        Fixture first = fixture();
        Fixture second = fixture();
        String occupied = QuarkusTransaction.requiringNew().call(() -> ((Account) Account.findById(second.id)).email);
        var exception = assertThrows(RecoveryService.RecoveryException.class,
                () -> recovery.requestEmail(first.code, occupied, Instant.now()));
        assertEquals("email_unavailable", exception.getCode());
    }

    private Fixture fixture() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.create("old-" + UUID.randomUUID() + "@test.invalid",
                    "America/Sao_Paulo", "pt-BR", Instant.now());
            account.persist();
            String code = recovery.generate(account, Instant.now());
            var session = sessions.create(account, Instant.now(), null, null);
            Account.getEntityManager().flush();
            Account.getEntityManager().refresh(account);
            return new Fixture(account.id, account.addressChangeDeadline, code, session.rawSessionToken());
        });
    }
    record Fixture(UUID id, Instant deadline, String code, String sessionToken) {}
}
