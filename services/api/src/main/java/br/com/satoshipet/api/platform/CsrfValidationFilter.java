package br.com.satoshipet.api.platform;

import br.com.satoshipet.api.account.Session;
import br.com.satoshipet.api.account.SessionTokenHasher;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Valida o token CSRF para mutations (POST, PATCH, DELETE) em sessões autenticadas.
 *
 * <p>Regras:
 * <ul>
 *   <li>GET, HEAD e OPTIONS são sempre permitidos.</li>
 *   <li>Paths em {@code /api/v1/auth/magic-link*} são excluídos (fluxo público).</li>
 *   <li>Se não há sessão autenticada, a validação CSRF é ignorada (o resource
 *       retornará 401 se necessário).</li>
 *   <li>Se há sessão, o header {@code X-CSRF-Token} deve conter o token bruto
 *       cujo hash corresponde ao {@code csrfTokenHash} da sessão.</li>
 * </ul>
 * </p>
 *
 * <p>Prioridade: {@link Priorities#AUTHORIZATION} (1000) — roda após o filtro
 * de autenticação.</p>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class CsrfValidationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(CsrfValidationFilter.class);

    /** Nome do header CSRF. */
    public static final String CSRF_HEADER = "X-CSRF-Token";

    /** Prefixo excluído da validação CSRF. */
    private static final String MAGIC_LINK_PREFIX = "/api/v1/auth/magic-link";

    @Inject
    AuthenticatedSession authenticatedSession;

    @Inject
    SessionTokenHasher hasher;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String method = requestContext.getMethod();

        // Somente mutations precisam de CSRF
        if ("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return;
        }

        // Caminhos magic-link são públicos e excluídos da validação
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith(MAGIC_LINK_PREFIX)) {
            return;
        }

        // Sem sessão → o resource vai rejeitar por autenticação; CSRF não se aplica
        if (!authenticatedSession.isAuthenticated()) {
            return;
        }

        Session session = authenticatedSession.get();
        String csrfHeader = requestContext.getHeaderString(CSRF_HEADER);

        if (csrfHeader == null || csrfHeader.isBlank()) {
            LOG.warnf("Header CSRF ausente para sessão %s method=%s path=%s",
                    session.id, method, path);
            requestContext.abortWith(
                    Response.status(403)
                            .entity(new CsrfErrorBody())
                            .build()
            );
            return;
        }

        String expectedHash = session.csrfTokenHash;
        String providedHash = hasher.hash(csrfHeader);

        if (!providedHash.equals(expectedHash)) {
            LOG.warnf("Token CSRF inválido para sessão %s method=%s path=%s",
                    session.id, method, path);
            requestContext.abortWith(
                    Response.status(403)
                            .entity(new CsrfErrorBody())
                            .build()
            );
        }
    }

    /** Corpo da resposta 403 por falha de validação CSRF. */
    public record CsrfErrorBody(String code, String message) {
        public CsrfErrorBody() {
            this("csrf_invalid", "Token CSRF inválido ou ausente.");
        }
    }
}
