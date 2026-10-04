package br.com.satoshipet.api.account;

import jakarta.ws.rs.core.NewCookie;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class SessionCookieFactoryTest {
    @Test
    void emissaoEExpiracaoAplicamPoliticaSeguraEPrazoConfigurado() {
        var cookies = new SessionCookieFactory(config(true));
        NewCookie issued = cookies.create("segredo-efemero");
        assertTrue(issued.isSecure());
        assertTrue(issued.isHttpOnly());
        assertEquals(NewCookie.SameSite.STRICT, issued.getSameSite());
        assertEquals("/", issued.getPath());
        assertEquals(3600, issued.getMaxAge());
        NewCookie expired = cookies.expire();
        assertTrue(expired.isSecure());
        assertTrue(expired.isHttpOnly());
        assertEquals(NewCookie.SameSite.STRICT, expired.getSameSite());
        assertEquals(0, expired.getMaxAge());
        assertEquals("", expired.getValue());
    }

    @Test
    void httpLocalExigeDesabilitacaoExplicita() {
        assertFalse(new SessionCookieFactory(config(false)).create("local").isSecure());
    }

    private SessionConfiguration config(boolean secure) {
        return new SessionConfiguration() {
            public Duration duration() { return Duration.ofHours(1); }
            public boolean secureCookies() { return secure; }
        };
    }
}
