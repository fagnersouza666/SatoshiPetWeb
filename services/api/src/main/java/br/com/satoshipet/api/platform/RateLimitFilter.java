package br.com.satoshipet.api.platform;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de requisições por IP (e futuramente por conta autenticada).
 *
 * <p>Implementação em memória com janela deslizante de 1 minuto.
 * Em produção com múltiplas réplicas, migrar para Redis
 * ({@code quarkus-redis-client}) para compartilhar o estado.</p>
 *
 * <p>Limites:
 * <ul>
 *   <li>Anônimo (por IP): 60 req/min</li>
 *   <li>Autenticado (por account): 300 req/min (reservado para implementação futura)</li>
 * </ul>
 * </p>
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter {

    /** Requisições máximas por IP por janela. */
    static final int MAX_REQUESTS_PER_WINDOW = 60;

    /** Duração da janela em milissegundos. */
    static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext request) {
        String ip = extractClientIp(request);
        WindowCounter counter = counters.computeIfAbsent(ip, k -> new WindowCounter());

        if (counter.incrementAndCheck(MAX_REQUESTS_PER_WINDOW, WINDOW_MS)) {
            return; // dentro do limite
        }

        request.abortWith(
                Response.status(429)
                        .header("Retry-After", "60")
                        .entity(new RateLimitBody())
                        .build()
        );
    }

    /** Extrai IP do cliente, considerando proxies reversos. */
    private String extractClientIp(ContainerRequestContext request) {
        String forwarded = request.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Pega o primeiro IP da cadeia (cliente original).
            return forwarded.split(",")[0].trim();
        }
        // Fallback: endereço remoto (via Vert.x, não disponível neste ponto;
        // em produção usar io.vertx.ext.web.RoutingContext injetado via CDI).
        return "unknown";
    }

    /** Corpo da resposta 429 padronizado com o catálogo de erros. */
    public record RateLimitBody(String code, String message) {
        public RateLimitBody() {
            this("rate_limited", "Muitas requisições. Tente novamente em 60 segundos.");
        }
    }

    /** Contador de requisições dentro de uma janela deslizante. */
    static final class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().toEpochMilli();

        /**
         * Incrementa o contador e verifica se ainda está dentro do limite.
         *
         * @return {@code true} se o limite NÃO foi excedido
         */
        boolean incrementAndCheck(int maxRequests, long windowMs) {
            long now = Instant.now().toEpochMilli();
            if (now - windowStart >= windowMs) {
                // Reinicia janela.
                synchronized (this) {
                    if (now - windowStart >= windowMs) {
                        count.set(0);
                        windowStart = now;
                    }
                }
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }
}
