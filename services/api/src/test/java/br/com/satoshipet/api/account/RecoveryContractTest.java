package br.com.satoshipet.api.account;

import br.com.satoshipet.api.mail.MailPort;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(RecoveryContractTest.Profile.class)
class RecoveryContractTest {
    @Inject SessionService sessions;
    @Inject RecoveryService recovery;

    public static class Profile implements QuarkusTestProfile {
        @Override public Set<Class<?>> getEnabledAlternatives() { return Set.of(CapturedMail.class); }
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("satoshi-pet.session.secure-cookies", "true",
                    "satoshi-pet.rate-limit.ip-limit", "1000");
        }
    }

    @Alternative
    @ApplicationScoped
    public static class CapturedMail implements MailPort {
        static final Map<String, String> links = new ConcurrentHashMap<>();
        @Override public void sendMagicLink(String to, String link) { links.put(to, link); }
    }

    @Test
    void codigoSemNovoEmailVerificadoNaoAbreSessao() {
        Fixture f = fixture();
        given().contentType(JSON).body(Map.of("code", f.code))
                .post("/api/v1/account/recovery/reset").then().statusCode(401);
        QuarkusTransaction.requiringNew().run(() -> assertEquals(1,
                RecoveryCode.count("account.id = ?1 AND usedAt IS NULL", f.id)));
    }

    @Test
    void verificaNovoEmailPreservaContaRevogaSessoesERenovaCodigo() {
        Fixture f = fixture();
        String email = "recovered-" + UUID.randomUUID() + "@test.invalid";
        String token = requestEmail(f.code, email);
        var response = given().contentType(JSON).body(Map.of("code", f.code, "token", token))
                .post("/api/v1/account/recovery/reset").then().statusCode(200).extract().response();
        String freshCode = response.jsonPath().getString("recoveryCode");
        assertNotNull(freshCode);
        assertNotEquals(f.code, freshCode);
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        QuarkusTransaction.requiringNew().run(() -> {
            Account account = Account.findById(f.id);
            assertEquals(email, account.email);
            assertEquals(f.deadline, account.addressChangeDeadline);
            assertTrue(sessions.findActive(f.sessionToken, Instant.now()).isEmpty());
            assertEquals(1, RecoveryCode.count("account.id = ?1 AND usedAt IS NULL", f.id));
        });
        given().contentType(JSON).body(Map.of("code", f.code, "token", token))
                .post("/api/v1/account/recovery/reset").then().statusCode(401);
    }

    @Test
    void duasRequisicoesConsomemCodigoUmaUnicaVez() throws Exception {
        Fixture f = fixture();
        String token = requestEmail(f.code, "race-" + UUID.randomUUID() + "@test.invalid");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return given().contentType(JSON).body(Map.of("code", f.code, "token", token))
                        .post("/api/v1/account/recovery/reset").statusCode();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertEquals(Set.of(200, 401), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        }
    }

    @Test
    void tokenDoNovoEmailNaoRecuperaOutraConta() {
        Fixture first = fixture();
        Fixture second = fixture();
        String token = requestEmail(first.code, "target-" + UUID.randomUUID() + "@test.invalid");
        given().contentType(JSON).body(Map.of("code", second.code, "token", token))
                .post("/api/v1/account/recovery/reset").then().statusCode(401);
        given().contentType(JSON).body(Map.of("code", first.code, "token", token))
                .post("/api/v1/account/recovery/reset").then().statusCode(200);
    }

    private String requestEmail(String code, String email) {
        given().contentType(JSON).body(Map.of("code", code, "email", email))
                .post("/api/v1/account/recovery/email").then().statusCode(202);
        String link = CapturedMail.links.remove(email);
        assertNotNull(link);
        assertTrue(link.contains("/recuperar?token="));
        return link.substring(link.indexOf("?token=") + 7);
    }

    private Fixture fixture() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Account account = Account.create("old-" + UUID.randomUUID() + "@test.invalid",
                    "America/Sao_Paulo", "pt-BR", Instant.now());
            account.persist();
            String code = recovery.generate(account, Instant.now());
            var session = sessions.create(account, Instant.now(), null, null);
            return new Fixture(account.id, account.addressChangeDeadline, code, session.rawSessionToken());
        });
    }
    record Fixture(UUID id, Instant deadline, String code, String sessionToken) {}
}
