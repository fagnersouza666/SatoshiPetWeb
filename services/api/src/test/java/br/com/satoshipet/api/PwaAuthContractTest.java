package br.com.satoshipet.api;

import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenHasher;
import br.com.satoshipet.api.account.MagicLinkTokenRepository;
import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Liga os serviços Angular de produção ao HTTP real do Quarkus com H2 efêmero.
 * Opt-in para que testes isolados da API não dependam da instalação da PWA.
 * A CI executa obrigatoriamente via infra/scripts/check-auth-integration.mjs.
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "pwa.auth.integration", matches = "true")
class PwaAuthContractTest {
    @Inject MagicLinkTokenRepository tokens;
    @Inject MagicLinkTokenHasher hasher;
    @Inject ObjectMapper json;
    @TestHTTPResource URI origin;

    @Test
    void productionPwaServicesRespectLiveAuthenticationContract() throws Exception {
        Map<String, Object> users = new LinkedHashMap<>();
        for (String name : new String[] { "signup", "existing", "csrf", "recovery", "logout", "forbidden", "network" }) {
            String email = "contract-" + name + "-" + UUID.randomUUID() + "@example.invalid";
            users.put(name, Map.of("email", email, "token", token(email), "loginToken", token(email)));
        }
        Path repository = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent().getParent();
        Path runner = repository.resolve("apps/pwa/integration/run-auth-api-contract.mjs");
        assertTrue(Files.isRegularFile(runner), "Executor do contrato PWA deve existir: " + runner);
        Path fixtures = Files.createTempFile("satoshi-pet-auth-fixtures-", ".json");
        Path output = Files.createTempFile("satoshi-pet-auth-output-", ".log");
        try {
            json.writeValue(fixtures.toFile(), Map.of(
                    "origin", origin.toString(), "address", BitcoinTestAddresses.MAINNET_BECH32, "users", users));
            Process process = new ProcessBuilder(
                    System.getenv().getOrDefault("SATOSHIPET_NODE", "node"),
                    runner.toString(), fixtures.toString())
                    .directory(repository.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            boolean finished = process.waitFor(150, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            // System.out preserva o protocolo do Surefire; inheritIO o corromperia.
            System.out.print(Files.readString(output));
            assertTrue(finished, "Contrato PWA/API excedeu 150 segundos");
            assertEquals(0, process.exitValue(), "Serviços de produção da PWA devem cumprir o contrato HTTP real");
        } finally {
            Files.deleteIfExists(fixtures);
            Files.deleteIfExists(output);
        }
    }

    private String token(String email) {
        String raw = "contract-" + UUID.randomUUID();
        Instant now = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> tokens.persist(
                MagicLinkToken.issue(email, hasher.hash(raw), now, now.plusSeconds(900))));
        return raw;
    }
}
