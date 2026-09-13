package br.com.satoshipet.api.platform;

import br.com.satoshipet.api.account.Session;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Limita requisições anônimas por IP e autenticadas por conta.
 *
 * <p>A implementação usa uma janela fixa em memória. Em produção com múltiplas
 * réplicas, o armazenamento deve ser substituído por um contador compartilhado
 * (por exemplo, Redis) para que o limite seja global.</p>
 *
 * <p>Limites:
 * <ul>
 *   <li>Anônimo (por IP): 60 req/min</li>
 *   <li>Autenticado (por conta): 300 req/min</li>
 * </ul>
 * </p>
 *
 * <p>O filtro roda depois da autenticação de sessão para que o contador de
 * conta seja aplicado antes da autorização e da execução do resource.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class RateLimitFilter implements ContainerRequestFilter {

    /** Limite padrão de requisições anônimas por IP. */
    static final int DEFAULT_MAX_REQUESTS_PER_IP = 60;

    /** Limite padrão de requisições autenticadas por conta. */
    static final int DEFAULT_MAX_REQUESTS_PER_ACCOUNT = 300;

    /** Janela padrão do rate limit. */
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEALTH_PATH = "/q/health";
    private static final String METRICS_PATH = "/q/metrics";

    @ConfigProperty(name = "satoshi-pet.rate-limit.ip-limit", defaultValue = "60")
    int maxRequestsPerIp = DEFAULT_MAX_REQUESTS_PER_IP;

    @ConfigProperty(name = "satoshi-pet.rate-limit.account-limit", defaultValue = "300")
    int maxRequestsPerAccount = DEFAULT_MAX_REQUESTS_PER_ACCOUNT;

    @ConfigProperty(name = "satoshi-pet.rate-limit.window", defaultValue = "PT1M")
    Duration window = DEFAULT_WINDOW;

    @Inject
    AuthenticatedSession authenticatedSession;

    /** Requisição Vert.x contém o endereço real quando nenhum proxy o informa. */
    @Context
    HttpServerRequest httpRequest;

    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Construtor CDI e fallback para testes unitários sem contexto HTTP. */
    public RateLimitFilter() {
        this(Clock.systemUTC());
    }

    RateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (isOperationalEndpoint(request)) {
            return;
        }

        Session session = authenticatedSession == null ? null : authenticatedSession.get();
        boolean authenticated = session != null
                && session.account != null
                && session.account.id != null;
        String key = authenticated
                ? "account:" + session.account.id
                : "ip:" + extractClientIp(request);
        int maxRequests = authenticated ? configuredAccountLimit() : configuredIpLimit();
        long windowMillis = configuredWindowMillis();

        long now = clock.millis();
        WindowCounter counter = counters.computeIfAbsent(key,
                ignored -> new WindowCounter(now));
        Decision decision = counter.tryAcquire(maxRequests, windowMillis, now);

        if (decision.allowed()) {
            return;
        }

        long retryAfterSeconds = toRetryAfterSeconds(decision.millisUntilReset());
        request.abortWith(
                Response.status(429)
                        .type(MediaType.APPLICATION_JSON)
                        .header("Retry-After", Long.toString(retryAfterSeconds))
                        .entity(new RateLimitBody(retryAfterSeconds))
                        .build()
        );
    }

    /** Não limita probes e scraping de endpoints operacionais. */
    private boolean isOperationalEndpoint(ContainerRequestContext request) {
        if (request.getUriInfo() == null) {
            return false;
        }
        String path = request.getUriInfo().getPath();
        if (path == null) {
            return false;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.equals(HEALTH_PATH) || path.startsWith(HEALTH_PATH + "/")
                || path.equals(METRICS_PATH) || path.startsWith(METRICS_PATH + "/");
    }

    private int configuredIpLimit() {
        return maxRequestsPerIp > 0 ? maxRequestsPerIp : DEFAULT_MAX_REQUESTS_PER_IP;
    }

    private int configuredAccountLimit() {
        return maxRequestsPerAccount > 0
                ? maxRequestsPerAccount
                : DEFAULT_MAX_REQUESTS_PER_ACCOUNT;
    }

    private long configuredWindowMillis() {
        if (window == null || window.isZero() || window.isNegative()) {
            return DEFAULT_WINDOW.toMillis();
        }
        try {
            return Math.max(1L, window.toMillis());
        } catch (ArithmeticException overflow) {
            return DEFAULT_WINDOW.toMillis();
        }
    }

    /** Extrai IP do cliente, considerando proxies reversos. */
    String extractClientIp(ContainerRequestContext request) {
        String forwarded = request.getHeaderString(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // Pega o primeiro IP da cadeia (cliente original).
            String firstHop = forwarded.split(",", 2)[0].trim();
            if (!firstHop.isBlank()) {
                return firstHop;
            }
        }

        if (httpRequest != null && httpRequest.remoteAddress() != null) {
            String host = httpRequest.remoteAddress().host();
            if (host != null && !host.isBlank()) {
                return host;
            }
        }

        // Fallback para contextos que não expõem o transporte (por exemplo,
        // testes unitários). Nunca usa uma chave de conta para requisição anônima.
        return "unknown";
    }

    /** Corpo da resposta 429 padronizado com o catálogo de erros. */
    public record RateLimitBody(String code, String message) {
        public RateLimitBody() {
            this(60);
        }

        public RateLimitBody(long retryAfterSeconds) {
            this("rate_limited", "Muitas requisições. Tente novamente em "
                    + retryAfterSeconds + " segundos.");
        }
    }

    private static long toRetryAfterSeconds(long millisUntilReset) {
        if (millisUntilReset <= 0) {
            return 1;
        }
        return millisUntilReset / 1_000L
                + (millisUntilReset % 1_000L == 0 ? 0 : 1);
    }

    /** Resultado de uma tentativa de consumo do limite. */
    record Decision(boolean allowed, long millisUntilReset) {
        static Decision accepted() {
            return new Decision(true, 0);
        }
    }

    /** Contador de requisições dentro de uma janela fixa, protegido por lock. */
    static final class WindowCounter {
        private long count;
        private long windowStart;

        WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }

        /**
         * Consome uma requisição se o limite ainda não foi atingido.
         *
         * @return decisão com o resultado e o tempo até a próxima janela
         */
        synchronized Decision tryAcquire(int maxRequests, long windowMs, long now) {
            long elapsed = now - windowStart;
            if (elapsed < 0 || elapsed >= windowMs) {
                count = 0;
                windowStart = now;
                elapsed = 0;
            }

            if (count >= maxRequests) {
                return new Decision(false, windowMs - elapsed);
            }

            count++;
            return Decision.accepted();
        }
    }
}
