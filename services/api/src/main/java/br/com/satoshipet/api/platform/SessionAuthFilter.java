package br.com.satoshipet.api.platform;

import br.com.satoshipet.api.account.Session;
import br.com.satoshipet.api.account.SessionService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

/**
 * Filtro de autenticação por cookie de sessão.
 *
 * <p>Lê o cookie {@code sp_session}, valida o token contra o banco e popula
 * o {@link AuthenticatedSession} do request. Não rejeita a requisição aqui —
 * a autorização ocorre no resource, que injeta {@link AuthenticatedSession}.</p>
 *
 * <p>Prioridade: {@link Priorities#AUTHENTICATION} (800) — roda antes de
 * filtros de negócio.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(SessionAuthFilter.class);

    /** Nome do cookie de sessão. */
    public static final String SESSION_COOKIE = "sp_session";

    @Inject
    SessionService sessionService;

    @Inject
    AuthenticatedSession authenticatedSession;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Cookie cookie = requestContext.getCookies().get(SESSION_COOKIE);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return; // sem cookie — request anônimo
        }

        String rawToken = cookie.getValue();
        Optional<Session> session = sessionService.findActive(rawToken, Instant.now());

        if (session.isPresent()) {
            authenticatedSession.set(session.get());
            LOG.tracef("Sessão autenticada: account=%s", session.get().account.id);
        } else {
            LOG.debugf("Cookie sp_session presente mas sessão inválida/expirada");
        }
    }
}
