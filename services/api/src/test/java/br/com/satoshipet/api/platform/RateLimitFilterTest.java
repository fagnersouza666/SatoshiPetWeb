package br.com.satoshipet.api.platform;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Session;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    @Test
    void limitaAnonimosPorIpEIsolaEnderecos() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        filter.window = Duration.ofSeconds(1);

        ContainerRequestContext primeiroIp = request("198.51.100.10");
        ContainerRequestContext segundoIp = request("198.51.100.11");

        filter.filter(primeiroIp);
        filter.filter(segundoIp);
        filter.filter(primeiroIp);

        var response = abortedResponse(primeiroIp);
        assertAll(
                () -> assertEquals(429, response.getStatus()),
                () -> assertEquals("1", response.getHeaderString("Retry-After")),
                () -> assertEquals("rate_limited", responseBody(response).code())
        );
        verify(segundoIp, never()).abortWith(any());
    }

    @Test
    void usaLimiteDaContaDepoisDaAutenticacao() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        filter.maxRequestsPerAccount = 2;

        var account = mock(Account.class);
        account.id = UUID.randomUUID();
        var session = mock(Session.class);
        session.account = account;
        var authenticatedSession = mock(AuthenticatedSession.class);
        when(authenticatedSession.get()).thenReturn(session);
        filter.authenticatedSession = authenticatedSession;

        ContainerRequestContext request = request("198.51.100.20");
        filter.filter(request);
        filter.filter(request);
        verify(request, never()).abortWith(any());

        filter.filter(request);

        var response = abortedResponse(request);
        assertEquals(429, response.getStatus());
        assertEquals("rate_limited", responseBody(response).code());
    }

    @Test
    void reiniciaJanelaAoExpirar() {
        var counter = new RateLimitFilter.WindowCounter(1_000L);

        assertEquals(true, counter.tryAcquire(1, 1_000L, 1_000L).allowed());
        var blocked = counter.tryAcquire(1, 1_000L, 1_999L);
        assertAll(
                () -> assertEquals(false, blocked.allowed()),
                () -> assertEquals(1L, blocked.millisUntilReset()),
                () -> assertEquals(true, counter.tryAcquire(1, 1_000L, 2_000L).allowed())
        );
    }

    @Test
    void naoLimitaEndpointsOperacionais() {
        var filter = new RateLimitFilter();
        filter.maxRequestsPerIp = 1;
        var request = request("/q/health", "198.51.100.30");

        filter.filter(request);
        filter.filter(request);

        verify(request, never()).abortWith(any());
    }

    private static ContainerRequestContext request(String ip) {
        return request("/api/v1/hello", ip);
    }

    private static ContainerRequestContext request(String path, String ip) {
        var request = mock(ContainerRequestContext.class);
        var uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaderString("X-Forwarded-For")).thenReturn(ip);
        return request;
    }

    private static Response abortedResponse(ContainerRequestContext request) {
        var response = org.mockito.ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(response.capture());
        return response.getValue();
    }

    private static RateLimitFilter.RateLimitBody responseBody(Response response) {
        return assertInstanceOf(RateLimitFilter.RateLimitBody.class, response.getEntity());
    }
}
