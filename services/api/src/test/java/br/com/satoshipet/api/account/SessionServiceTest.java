package br.com.satoshipet.api.account;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SessionServiceTest {

    @Inject
    SessionService sessionService;

    @Inject
    SessionTokenHasher hasher;

    @Test
    @Transactional
    void criaSessionComTokenHashPersistido() {
        Account account = criarConta("sessao-" + UUID.randomUUID() + "@test.com");

        Instant now = Instant.now();
        SessionService.SessionCreation creation = sessionService.create(account, now, "UA-Test", "127.0.0.1");

        assertNotNull(creation.rawSessionToken());
        assertNotNull(creation.rawCsrfToken());
        assertNotNull(creation.session());

        Session saved = (Session) Session.findById(creation.session().id);
        assertNotNull(saved);
        assertEquals(hasher.hash(creation.rawSessionToken()), saved.tokenHash);
        assertTrue(saved.isValidAt(now.plusSeconds(60)));
    }

    @Test
    @Transactional
    void findActiveRetornaSessionParaTokenValido() {
        Account account = criarConta("findactive-" + UUID.randomUUID() + "@test.com");
        Instant now = Instant.now();
        SessionService.SessionCreation creation = sessionService.create(account, now, null, null);

        Optional<Session> found = sessionService.findActive(creation.rawSessionToken(), now.plusSeconds(10));

        assertTrue(found.isPresent());
        assertEquals(creation.session().id, found.get().id);
    }

    @Test
    @Transactional
    void findActiveRetornaVazioParaTokenInvalido() {
        Optional<Session> found = sessionService.findActive("token-inexistente", Instant.now());
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void revokeInvalidaSession() {
        Account account = criarConta("revoke-" + UUID.randomUUID() + "@test.com");
        Instant now = Instant.now();
        SessionService.SessionCreation creation = sessionService.create(account, now, null, null);

        sessionService.revoke(creation.rawSessionToken(), now.plusSeconds(1));

        Optional<Session> found = sessionService.findActive(creation.rawSessionToken(), now.plusSeconds(2));
        assertFalse(found.isPresent(), "Sessão revogada não deve ser encontrada como ativa");
    }

    @Test
    @Transactional
    void revokeAllInvalidaTodasSessionsDaConta() {
        Account account = criarConta("revokeall-" + UUID.randomUUID() + "@test.com");
        Instant now = Instant.now();
        SessionService.SessionCreation c1 = sessionService.create(account, now, null, null);
        SessionService.SessionCreation c2 = sessionService.create(account, now.plusSeconds(1), null, null);

        sessionService.revokeAll(account.id, now.plusSeconds(5));

        assertFalse(sessionService.findActive(c1.rawSessionToken(), now.plusSeconds(10)).isPresent());
        assertFalse(sessionService.findActive(c2.rawSessionToken(), now.plusSeconds(10)).isPresent());
    }

    private Account criarConta(String email) {
        Account account = Account.create(email, "America/Sao_Paulo", "pt-BR", Instant.now());
        account.persist();
        return account;
    }
}
