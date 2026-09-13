package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.MagicLinkTokenConfiguration;
import br.com.satoshipet.api.account.MagicLinkTokenService;
import br.com.satoshipet.api.auth.magiclink.MagicLinkErrorResponse;
import br.com.satoshipet.api.auth.magiclink.MagicLinkFieldError;
import br.com.satoshipet.api.auth.magiclink.MagicLinkFieldErrorCode;
import br.com.satoshipet.api.auth.magiclink.MagicLinkRequest;
import br.com.satoshipet.api.auth.magiclink.MagicLinkRequestResponse;
import br.com.satoshipet.api.mail.MailPort;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resource para iniciar o fluxo de autenticação por magic link.
 *
 * <p>POST /api/v1/auth/magic-link
 *
 * <p>Resposta uniforme: 202 Accepted para qualquer e-mail válido (não revela
 * se a conta existe). Erros retornam 400 (dados inválidos) ou 429 (rate limit).
 */
@Path("/api/v1/auth/magic-link")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MagicLinkResource {

    private static final Logger LOG = Logger.getLogger(MagicLinkResource.class);

    /** Comprimento em bytes do token de magic link (256 bits). */
    private static final int TOKEN_BYTES = 32;

    @Inject
    MagicLinkTokenService tokenService;

    @Inject
    MailPort mailPort;

    @Inject
    Validator validator;

    @Inject
    MagicLinkTokenConfiguration magicLinkConfiguration;

    private final SecureRandom random = new SecureRandom();

    /**
     * Inicia o fluxo de autenticação enviando o magic link para o e-mail informado.
     *
     * <p>Resposta é uniforme (202) independentemente de o e-mail estar cadastrado,
     * conforme CC-01 (não permite enumeração de contas).
     */
    @POST
    public Response requestMagicLink(MagicLinkRequest body, @Context ContainerRequestContext ctx) {
        // Validação do body
        if (body == null) {
            return Response.status(400)
                    .entity(MagicLinkErrorResponse.invalidRequest(List.of()))
                    .build();
        }

        Set<ConstraintViolation<MagicLinkRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            List<MagicLinkFieldError> fieldErrors = violations.stream()
                    .map(v -> new MagicLinkFieldError(
                            v.getPropertyPath().toString(),
                            toFieldErrorCode(v),
                            v.getMessage()
                    ))
                    .collect(Collectors.toList());
            return Response.status(400)
                    .entity(MagicLinkErrorResponse.invalidRequest(fieldErrors))
                    .build();
        }

        try {
            String rawToken = generateToken();
            String link = magicLinkConfiguration.baseUrl() + "/entrar/verificar?token=" + rawToken;
            Instant now = Instant.now();

            // Persiste somente o hash; token bruto vai no e-mail
            tokenService.issue(body.email(), rawToken, now);

            // O link bruto não é registrado em log (invariante de segurança)
            mailPort.sendMagicLink(body.email(), link);

            return Response.accepted()
                    .entity(MagicLinkRequestResponse.accepted())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Falha ao emitir magic link para e-mail (omitido do log)");
            return Response.status(503)
                    .entity(MagicLinkErrorResponse.requestUnavailable())
                    .build();
        }
    }

    /** Gera token aleatório seguro em Base64Url sem padding. */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Converte violação de constraint em código de erro de campo. */
    private MagicLinkFieldErrorCode toFieldErrorCode(ConstraintViolation<MagicLinkRequest> v) {
        String annotationName = v.getConstraintDescriptor().getAnnotation()
                .annotationType().getSimpleName();
        return switch (annotationName) {
            case "Email" -> MagicLinkFieldErrorCode.INVALID_FORMAT;
            case "Size"  -> MagicLinkFieldErrorCode.TOO_LONG;
            default      -> MagicLinkFieldErrorCode.REQUIRED;
        };
    }
}
