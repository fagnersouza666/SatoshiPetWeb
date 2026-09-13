package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenService;
import br.com.satoshipet.api.account.Session;
import br.com.satoshipet.api.account.SessionService;
import br.com.satoshipet.api.platform.SessionAuthFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

/**
 * Resource para verificar o magic link e iniciar a sessão ou indicar
 * necessidade de registro.
 *
 * <p>POST /api/v1/auth/magic-link/verify
 *
 * <p>Comportamento:
 * <ul>
 *   <li>Token inválido ou expirado: 401 opaco (não revela o motivo).</li>
 *   <li>Token válido, conta existente: consome token, cria sessão, retorna cookie
 *       sp_session + header X-CSRF-Token e {@code registrationRequired=false}.</li>
 *   <li>Token válido, conta não existe: NÃO consome o token, retorna
 *       {@code registrationRequired=true} para que o cliente prossiga ao registro.</li>
 * </ul>
 * </p>
 */
@Path("/api/v1/auth/magic-link/verify")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MagicLinkVerifyResource {

    private static final Logger LOG = Logger.getLogger(MagicLinkVerifyResource.class);

    @Inject
    MagicLinkTokenService tokenService;

    @Inject
    SessionService sessionService;

    /**
     * Verifica o token do magic link.
     *
     * @param body body com o token bruto
     * @return 200 com resultado da verificação, ou 401 opaco
     */
    @POST
    public Response verify(VerifyRequest body, @Context ContainerRequestContext ctx) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            return unauthorizedOpaque();
        }

        Instant now = Instant.now();
        String rawToken = body.token();

        // Verifica se o token é válido (sem consumir ainda)
        String tokenHash = hash(rawToken);
        Optional<br.com.satoshipet.api.account.MagicLinkToken> candidate = tokenService.peekByRawToken(rawToken, now);

        if (candidate.isEmpty()) {
            LOG.debugf("Token de magic link inválido ou expirado");
            return unauthorizedOpaque();
        }

        MagicLinkToken token = candidate.get();
        String email = token.getEmail();

        // Verifica se existe conta com esse e-mail
        Account account = Account.findByEmail(email);

        if (account == null) {
            // Nova conta necessária — não consome o token, cliente usa-o no /register
            LOG.debugf("Conta não encontrada para magic link — registro necessário");
            return Response.ok(new VerifyResponse("ok", true, email)).build();
        }

        // Conta existente — consome o token e cria sessão
        Optional<MagicLinkToken> consumed = tokenService.consume(rawToken, now);
        if (consumed.isEmpty()) {
            // Corrida entre duas requisições — falha opaca
            return unauthorizedOpaque();
        }

        String userAgent = ctx.getHeaderString("User-Agent");
        String ip = extractIp(ctx);
        SessionService.SessionCreation creation = sessionService.create(account, now, userAgent, ip);

        NewCookie sessionCookie = buildSessionCookie(creation.rawSessionToken());

        return Response.ok(new VerifyResponse("ok", false, email))
                .cookie(sessionCookie)
                .header("X-CSRF-Token", creation.rawCsrfToken())
                .build();
    }

    /** Gera o hash SHA-256 do token (apenas para verificação prévia via peek). */
    private String hash(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private Response unauthorizedOpaque() {
        return Response.status(401)
                .entity(new ErrorBody("unauthorized", "Acesso não autorizado."))
                .build();
    }

    private NewCookie buildSessionCookie(String rawToken) {
        return new NewCookie.Builder(SessionAuthFilter.SESSION_COOKIE)
                .value(rawToken)
                .path("/")
                .httpOnly(true)
                .secure(false) // em produção habilitar via reverse proxy com HTTPS
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(60 * 60 * 24 * 30) // 30 dias em segundos
                .build();
    }

    private String extractIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    /** Body da requisição de verificação. */
    public record VerifyRequest(String token) {}

    /** Resposta de verificação bem-sucedida. */
    public record VerifyResponse(String status, boolean registrationRequired, String verifiedEmail) {}

    /** Envelope de erro. */
    public record ErrorBody(String code, String message) {}
}
