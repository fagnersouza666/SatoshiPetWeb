package br.com.satoshipet.api.platform;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Session;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {
    @Test
    void limitaAnonimosPorIpEIsolaEnderecos() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        filter.window = Duration.ofSeconds(1);
        Request first = request("198.51.100.10");
        Request second = request("198.51.100.11");
        apply(filter, first); apply(filter, second); apply(filter, first);
        assertEquals(429, first.aborted.get().getStatus());
        assertEquals("1", first.aborted.get().getHeaderString("Retry-After"));
        assertEquals("rate_limited", ((RateLimitFilter.RateLimitBody) first.aborted.get().getEntity()).code());
        assertNull(second.aborted.get());
    }

    @Test
    void usaLimiteDaContaDepoisDaAutenticacao() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        filter.maxRequestsPerAccount = 2;
        Instant now = Instant.now();
        Account account = Account.create("test@test.invalid", "UTC", "pt-BR", now);
        Session session = Session.create(account, "hash", "csrf", now, now.plusSeconds(60), null, null);
        filter.authenticatedSession = new AuthenticatedSession();
        filter.authenticatedSession.set(session);
        Request request = request("198.51.100.20");
        apply(filter, request); apply(filter, request);
        assertNull(request.aborted.get());
        apply(filter, request);
        assertEquals(429, request.aborted.get().getStatus());
    }

    @Test
    void reiniciaJanelaAoExpirar() {
        var counter = new RateLimitFilter.WindowCounter(1000L);
        assertTrue(counter.tryAcquire(1, 1000, 1000).allowed());
        assertFalse(counter.tryAcquire(1, 1000, 1999).allowed());
        assertEquals(1, counter.tryAcquire(1, 1000, 1999).millisUntilReset());
        assertTrue(counter.tryAcquire(1, 1000, 2000).allowed());
    }

    @Test
    void naoLimitaEndpointsOperacionais() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        Request request = request("/q/health", "198.51.100.30", "198.51.100.30");
        apply(filter, request); apply(filter, request);
        assertNull(request.aborted.get());
    }

    @Test
    void descartaIdentidadesInativasAposJanelaSemReabrirCotaAtiva() throws Exception {
        AtomicLong time = new AtomicLong(1000);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneId.of("UTC"); }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(time.get()); }
        };
        var filter = new RateLimitFilter(clock);
        filter.maxRequestsPerIp = 1;
        filter.window = Duration.ofSeconds(1);
        for (int i = 0; i < 100; i++) apply(filter, request("198.51.100." + i));
        time.set(2100);
        apply(filter, request("203.0.113.1"));
        var field = RateLimitFilter.class.getDeclaredField("counters");
        field.setAccessible(true);
        assertEquals(1, ((Map<?, ?>) field.get(filter)).size());
        Request active = request("203.0.113.1");
        apply(filter, active);
        assertEquals(429, active.aborted.get().getStatus());
    }

    @Test
    void headerForwardedForNaoPermiteTrocarIdentidadeSemProxyConfiavel() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        Request first = request("/api/v1/hello", "198.51.100.1", "203.0.113.1");
        Request spoofed = request("/api/v1/hello", "198.51.100.2", "203.0.113.1");
        apply(filter, first); apply(filter, spoofed);
        assertNotNull(spoofed.aborted.get());
        assertEquals(429, spoofed.aborted.get().getStatus());
    }

    private static void apply(RateLimitFilter filter, Request request) {
        filter.httpRequest = (HttpServerRequest) Proxy.newProxyInstance(HttpServerRequest.class.getClassLoader(),
                new Class<?>[]{HttpServerRequest.class}, (p, m, args) ->
                        m.getName().equals("remoteAddress") ? SocketAddress.inetSocketAddress(443, request.remote) : null);
        filter.filter(request.context);
    }

    private static Request request(String ip) { return request("/api/v1/hello", ip, ip); }
    private static Request request(String path, String header, String remote) {
        var aborted = new AtomicReference<Response>();
        UriInfo uri = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(), new Class<?>[]{UriInfo.class},
                (p, m, args) -> m.getName().equals("getPath") ? path : null);
        ContainerRequestContext context = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (p, m, args) -> switch (m.getName()) {
                    case "getHeaderString" -> header;
                    case "getUriInfo" -> uri;
                    case "abortWith" -> { aborted.set((Response) args[0]); yield null; }
                    default -> null;
                });
        return new Request(context, aborted, remote);
    }
    record Request(ContainerRequestContext context, AtomicReference<Response> aborted, String remote) {}
}
