package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.*;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import br.com.satoshipet.api.platform.ClientAddress;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MagicLinkRecoveryRaceTest {
    @Inject MagicLinkVerifyResource verify;
    @Inject MagicLinkTokenService links;
    @Inject RecoveryService recovery;
    @BeforeEach
    void configureTransport() {
        ClientAddress clientAddress = org.mockito.Mockito.mock(ClientAddress.class);
        org.mockito.Mockito.when(clientAddress.value()).thenReturn("127.0.0.1");
        QuarkusMock.installMockForType(clientAddress, ClientAddress.class);
    }

    @Test
    void magicLinkAntigoNaoAbreSessaoDepoisDaRecuperacao() throws Exception {
        String email = "login-race-" + UUID.randomUUID() + "@test.invalid";
        String link = "link-race-" + UUID.randomUUID();
        Fixture fixture = QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.create(email, "America/Sao_Paulo", "pt-BR", Instant.now());
            account.persist();
            return new Fixture(account.id, recovery.generate(account, Instant.now()));
        });
        links.issue(email, link, Instant.now());
        String newEmail = "recovered-" + UUID.randomUUID() + "@test.invalid";
        var challenge = recovery.requestEmail(fixture.code, newEmail, Instant.now());
        CountDownLatch loginConsumed = new CountDownLatch(1);
        CountDownLatch finishLogin = new CountDownLatch(1);
        CountDownLatch recovered = new CountDownLatch(1);
        ContainerRequestContext context = (ContainerRequestContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeaderString")) {
                        loginConsumed.countDown();
                        assertTrue(finishLogin.await(10, TimeUnit.SECONDS));
                        return "test-agent";
                    }
                    return null;
                });

        boolean recoveryFinishedBeforeLogin;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> {
                var request = Arc.container().requestContext();
                request.activate();
                try {
                    return verify.verify(new MagicLinkVerifyResource.VerifyRequest(link), context).getStatus();
                } finally { request.terminate(); }
            });
            assertTrue(loginConsumed.await(10, TimeUnit.SECONDS));
            var reset = executor.submit(() -> {
                try {
                    return recovery.recover(fixture.code, challenge.token(), Instant.now(), null, null);
                } finally { recovered.countDown(); }
            });
            try {
                recoveryFinishedBeforeLogin = recovered.await(300, TimeUnit.MILLISECONDS);
            } finally { finishLogin.countDown(); }
            assertEquals(200, login.get(15, TimeUnit.SECONDS));
            assertNotNull(reset.get(15, TimeUnit.SECONDS));
        } finally { finishLogin.countDown(); }

        assertFalse(recoveryFinishedBeforeLogin,
                "Recuperação e consumo do link/criação da sessão precisam da mesma trava transacional");
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(newEmail, ((Account) Account.findById(fixture.id)).email);
            assertEquals(1, Session.count("account.id = ?1 AND invalidatedAt IS NULL", fixture.id),
                    "Somente a sessão da recuperação pode continuar ativa");
        });
    }

    record Fixture(UUID id, String code) {}
}
