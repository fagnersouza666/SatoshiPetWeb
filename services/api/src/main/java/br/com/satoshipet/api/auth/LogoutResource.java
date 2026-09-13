package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.SessionService;
import br.com.satoshipet.api.platform.AuthenticatedSession;
import br.com.satoshipet.api.platform.SessionAuthFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Resource para encerramento de sessão.
 *
 * <p>POST /api/v1/auth/logout
 *
 * <p>Invalida a sessão associada ao cookie {@code sp_session} e limpa o cookie
 * no cliente. Requer sessão autenticada + token CSRF válido.
 */
@Path("/api/v1/auth/logout")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LogoutResource {

    private static final Logger LOG = Logger.getLogger(LogoutResource.class);

    @Inject
    AuthenticatedSession authenticatedSession;

    @Inject
    SessionService sessionService;

    /**
     * Revoga a sessão atual.
     *
     * @return 204 No Content com cookie expirado
     */
    @POST
    public Response logout(@jakarta.ws.rs.core.Context jakarta.ws.rs.container.ContainerRequestContext ctx) {
        if (!authenticatedSession.isAuthenticated()) {
            return Response.status(401)
                    .entity(new ErrorBody("unauthorized", "Sessão não encontrada."))
                    .build();
        }

        // Revoga via hash (relê o cookie bruto)
        Cookie cookie = ctx.getCookies().get(SessionAuthFilter.SESSION_COOKIE);
        if (cookie != null && cookie.getValue() != null) {
            sessionService.revoke(cookie.getValue(), Instant.now());
        }

        LOG.infof("Logout realizado para account=%s", authenticatedSession.get().account.id);

        // Expira o cookie no cliente
        NewCookie expiredCookie = new NewCookie.Builder(SessionAuthFilter.SESSION_COOKIE)
                .value("")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();

        return Response.noContent()
                .cookie(expiredCookie)
                .build();
    }

    /** Envelope de erro. */
    public record ErrorBody(String code, String message) {}
}
