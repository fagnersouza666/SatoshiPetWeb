package br.com.satoshipet.api.auth;

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

/**
 * Resource para criar uma nova conta após verificação do magic link.
 *
 * <p>POST /api/v1/auth/register
 *
 * <p>Fluxo: o cliente deve ter verificado previamente o magic link e obtido
 * {@code registrationRequired=true}. O mesmo token é reutilizado aqui e
 * consumido definitivamente neste passo.
 */
@Path("/api/v1/auth/register")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegistrationResource {

    private static final Logger LOG = Logger.getLogger(RegistrationResource.class);

    @Inject
    RegistrationService registrationService;

    /**
     * Cria a conta, address, pet e sessão em uma única operação atômica.
     *
     * @param body dados de registro
     * @return 201 Created com cookie de sessão e header CSRF, ou 400/409/422 em erro
     */
    @POST
    public Response register(RegisterRequest body, @Context ContainerRequestContext ctx) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            return badRequest("token_required", "O token de verificação é obrigatório.");
        }
        if (body.bitcoinAddress() == null || body.bitcoinAddress().isBlank()) {
            return badRequest("address_required", "O endereço Bitcoin é obrigatório.");
        }
        if (body.petName() == null || body.petName().isBlank()) {
            return badRequest("pet_name_required", "O nome do pet é obrigatório.");
        }

        try {
            String userAgent = ctx.getHeaderString("User-Agent");
            String ip = extractIp(ctx);
            SessionService.SessionCreation creation = registrationService.register(
                    body.token(),
                    body.bitcoinAddress(),
                    body.petName(),
                    body.timezone(),
                    userAgent,
                    ip,
                    Instant.now()
            );

            NewCookie sessionCookie = buildSessionCookie(creation.rawSessionToken());

            return Response.status(201)
                    .cookie(sessionCookie)
                    .header("X-CSRF-Token", creation.rawCsrfToken())
                    .entity(new RegisterResponse("ok", creation.session().account.id.toString()))
                    .build();

        } catch (RegistrationService.RegistrationException e) {
            int status = "account_exists".equals(e.getCode()) ? 409 : 422;
            return Response.status(status)
                    .entity(new ErrorBody(e.getCode(), e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Falha inesperada no registro");
            return Response.serverError()
                    .entity(new ErrorBody("internal_error", "Falha inesperada. Tente novamente."))
                    .build();
        }
    }

    private Response badRequest(String code, String message) {
        return Response.status(400).entity(new ErrorBody(code, message)).build();
    }

    private NewCookie buildSessionCookie(String rawToken) {
        return new NewCookie.Builder(SessionAuthFilter.SESSION_COOKIE)
                .value(rawToken)
                .path("/")
                .httpOnly(true)
                .secure(false)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(60 * 60 * 24 * 30)
                .build();
    }

    private String extractIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    /** Body da requisição de registro. */
    public record RegisterRequest(
            String token,
            String bitcoinAddress,
            String petName,
            String timezone
    ) {}

    /** Resposta de registro bem-sucedido. */
    public record RegisterResponse(String status, String accountId) {}

    /** Envelope de erro. */
    public record ErrorBody(String code, String message) {}
}
