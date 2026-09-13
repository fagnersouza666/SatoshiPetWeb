package br.com.satoshipet.api.platform;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityHeadersFilterTest {

    @Test
    void adicionaPoliticaCspCompatívelComPwa() {
        var response = mock(ContainerResponseContext.class);
        var headers = new MultivaluedHashMap<String, Object>();
        when(response.getHeaders()).thenReturn(headers);

        new SecurityHeadersFilter().filter(mock(ContainerRequestContext.class), response);

        var csp = (String) headers.getFirst("Content-Security-Policy");
        assertAll(
                () -> assertTrue(csp.contains("default-src 'self'")),
                () -> assertTrue(csp.contains("script-src 'self'")),
                () -> assertTrue(csp.contains("style-src 'self' 'unsafe-inline'")),
                () -> assertTrue(csp.contains("connect-src 'self' wss:")),
                () -> assertTrue(csp.contains("worker-src 'self'")),
                () -> assertTrue(csp.contains("manifest-src 'self'")),
                () -> assertTrue(csp.contains("object-src 'none'")),
                () -> assertTrue(csp.contains("frame-ancestors 'none'")));
    }
}
