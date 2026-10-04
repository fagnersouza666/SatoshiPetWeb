package br.com.satoshipet.api.account;

import br.com.satoshipet.api.platform.SessionAuthFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.NewCookie;

/** Política única para emissão e expiração do cookie HttpOnly. */
@ApplicationScoped
public class SessionCookieFactory {
    private final SessionConfiguration configuration;

    public SessionCookieFactory(SessionConfiguration configuration) {
        this.configuration = configuration;
    }

    public NewCookie create(String token) {
        return cookie(token, Math.toIntExact(configuration.duration().toSeconds()));
    }

    public NewCookie expire() {
        return cookie("", 0);
    }

    private NewCookie cookie(String value, int maxAge) {
        return new NewCookie.Builder(SessionAuthFilter.SESSION_COOKIE)
                .value(value).path("/").httpOnly(true)
                .secure(configuration.secureCookies()).sameSite(NewCookie.SameSite.STRICT)
                .maxAge(maxAge).build();
    }
}
