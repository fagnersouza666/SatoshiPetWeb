package br.com.satoshipet.api.account;

import br.com.satoshipet.api.isolation.AccountPrivateDataWipePort;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.platform.AuthenticatedSession;
import br.com.satoshipet.api.platform.SessionAuthFilter;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

/**
 * Resource para operações da conta autenticada.
 *
 * <p>Todos os endpoints exigem sessão válida (verificada via {@link AuthenticatedSession}).
 */
@Path("/api/v1/account")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    private static final Logger LOG = Logger.getLogger(AccountResource.class);

    @Inject
    AuthenticatedSession authenticatedSession;

    @Inject
    AccountAddressChangeService addressChangeService;

    @Inject
    RecoveryService recoveryService;

    @Inject
    SessionService sessionService;

    @Inject
    AccountPrivateDataWipePort wipePort;

    @Inject
    SessionCookieFactory cookies;

    @Inject br.com.satoshipet.api.mail.MailPort mail;
    @Inject MagicLinkTokenConfiguration magicLinkConfiguration;

    /**
     * Retorna dados da conta autenticada.
     *
     * <p>GET /api/v1/account/me
     */
    @GET
    @Path("/me")
    public Response me() {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        Account account = authenticatedSession.get().account;
        Optional<AccountAddressBinding> binding = AccountAddressBinding.findActivePrimary(account);

        String canonicalAddress = binding.map(b -> b.address.canonical).orElse(null);
        Optional<Pet> pet = binding.flatMap(b -> Pet.findByAddress(b.address));

        AccountResponse resp = new AccountResponse(
                account.id.toString(),
                account.email,
                account.timezone,
                account.locale,
                canonicalAddress,
                pet.map(p -> p.name).orElse(null),
                account.addressChangeDeadline != null ? account.addressChangeDeadline.toString() : null
        );
        return Response.ok(resp).header("Cache-Control", "no-store")
                .header("X-CSRF-Token", authenticatedSession.csrfToken()).build();
    }

    /**
     * Troca o endereço Bitcoin dentro da janela de 72h.
     *
     * <p>POST /api/v1/account/address/change
     */
    @POST
    @Path("/address/change")
    public Response changeAddress(AddressChangeRequest body) {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        if (body == null || body.bitcoinAddress() == null || body.bitcoinAddress().isBlank()) {
            return badRequest("address_required", "O endereço Bitcoin é obrigatório.");
        }

        try {
            Address newAddress = addressChangeService.change(
                    authenticatedSession.get().account,
                    body.bitcoinAddress(), body.petName(),
                    Instant.now()
            );
            return Response.ok(new AddressChangeResponse("ok", newAddress.canonical)).build();
        } catch (AccountAddressChangeService.AddressChangeException e) {
            int status = "window_expired".equals(e.getCode()) ? 403 : 422;
            return Response.status(status).entity(new ErrorBody(e.getCode(), e.getMessage())).build();
        }
    }

    /**
     * Renomeia o pet se a conta for a criadora.
     *
     * <p>PATCH /api/v1/account/pet/name
     */
    @PATCH
    @Path("/pet/name")
    @Transactional
    public Response renamePet(PetNameRequest body) {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        if (body == null || body.name() == null || body.name().isBlank()) {
            return badRequest("name_required", "O nome do pet é obrigatório.");
        }
        String trimmed = body.name().trim();
        if (trimmed.length() > 100) {
            return badRequest("name_too_long", "O nome do pet deve ter no máximo 100 caracteres.");
        }

        Account account = authenticatedSession.get().account;
        Optional<AccountAddressBinding> binding = AccountAddressBinding.findActivePrimary(account);
        if (binding.isEmpty()) {
            return badRequest("no_address", "Nenhum endereço vinculado à conta.");
        }

        Optional<Pet> petOpt = Pet.findByAddress(binding.get().address);
        if (petOpt.isEmpty()) {
            return badRequest("no_pet", "Nenhum pet encontrado para o endereço.");
        }
        Pet pet = Pet.lockForUpdate(petOpt.get().id);

        // Somente o criador pode renomear (PRD §5)
        if (pet.creatorAccount == null || !pet.creatorAccount.id.equals(account.id)
                || !AccountAddressBinding.isActivelyBound(account, pet.address)) {
            return Response.status(403)
                    .entity(new ErrorBody("not_creator", "Apenas o criador do pet pode renomeá-lo."))
                    .build();
        }

        pet.name = trimmed;
        pet.updatedAt = Instant.now();

        return Response.ok(new PetNameResponse("ok", pet.name)).build();
    }

    /**
     * Gera um código de recuperação (apresentado uma única vez).
     *
     * <p>POST /api/v1/account/recovery/code
     */
    @POST
    @Path("/recovery/code")
    public Response generateRecoveryCode() {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        String rawCode = recoveryService.generate(
                authenticatedSession.get().account,
                Instant.now()
        );
        return Response.ok(new RecoveryCodeResponse(rawCode)).header("Cache-Control", "no-store").build();
    }

    /**
     * Usa um código de recuperação para criar nova sessão.
     *
     * <p>POST /api/v1/account/recovery/reset
     */
    @POST
    @Path("/recovery/email")
    public Response requestRecoveryEmail(RecoveryEmailRequest body) {
        if (body == null || body.code() == null || body.email() == null) {
            return badRequest("recovery_fields_required", "Informe o código de recuperação e o novo e-mail.");
        }
        try {
            var verification = recoveryService.requestEmail(body.code(), body.email(), Instant.now());
            mail.sendMagicLink(verification.email(), magicLinkConfiguration.baseUrl()
                    + "/recuperar?token=" + verification.token());
            return Response.accepted().header("Cache-Control", "no-store")
                    .entity(new RecoveryEmailResponse("accepted")).build();
        } catch (RecoveryService.RecoveryException e) {
            int status = "invalid_email".equals(e.getCode()) ? 400
                    : "email_unavailable".equals(e.getCode()) ? 409 : 401;
            return Response.status(status).entity(new ErrorBody(e.getCode(), e.getMessage())).build();
        } catch (Exception e) {
            LOG.warn("Falha ao enviar verificação de recuperação (segredos omitidos)");
            return Response.status(503).entity(new ErrorBody("email_unavailable", "Tente enviar o link novamente.")).build();
        }
    }

    @POST
    @Path("/recovery/reset")
    public Response recoveryReset(RecoveryResetRequest body, @Context ContainerRequestContext ctx) {
        if (body == null || body.code() == null || body.code().isBlank()) {
            return badRequest("code_required", "O código de recuperação é obrigatório.");
        }
        try {
            String userAgent = ctx.getHeaderString("User-Agent");
            String ip = extractIp(ctx);
            RecoveryService.RecoveryResult recovered = recoveryService.recover(
                    body.code(), body.token(), Instant.now(), userAgent, ip
            );
            SessionService.SessionCreation creation = recovered.session();
            NewCookie sessionCookie = cookies.create(creation.rawSessionToken());
            return Response.ok(new RecoveryResetResponse("ok", recovered.recoveryCode()))
                    .cookie(sessionCookie)
                    .header("Cache-Control", "no-store")
                    .header("X-CSRF-Token", creation.rawCsrfToken())
                    .build();
        } catch (RecoveryService.RecoveryException e) {
            return Response.status(401)
                    .entity(new ErrorBody(e.getCode(), e.getMessage()))
                    .build();
        }
    }

    /**
     * Apaga todos os dados privados da conta (LGPD, CA-070).
     *
     * <p>DELETE /api/v1/account
     */
    @DELETE
    public Response deleteAccount(@Context ContainerRequestContext ctx) {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        Account account = authenticatedSession.get().account;
        wipePort.wipe(account.id);

        LOG.infof("Conta apagada (wipe): account=%s", account.id);

        NewCookie expiredCookie = cookies.expire();

        return Response.noContent().cookie(expiredCookie).build();
    }

    private Response unauthorized() {
        return Response.status(401).entity(new ErrorBody("unauthorized", "Autenticação necessária.")).build();
    }

    private Response badRequest(String code, String message) {
        return Response.status(400).entity(new ErrorBody(code, message)).build();
    }

    @Inject
    br.com.satoshipet.api.platform.ClientAddress clientAddress;

    private String extractIp(ContainerRequestContext ctx) {
        return clientAddress.value();
    }

    // --- DTOs ---

    public record AccountResponse(
            String id,
            String email,
            String timezone,
            String locale,
            String bitcoinAddress,
            String petName,
            String addressChangeDeadline
    ) {}

    public record AddressChangeRequest(String bitcoinAddress, String petName) {}
    public record AddressChangeResponse(String status, String canonical) {}

    public record PetNameRequest(String name) {}
    public record PetNameResponse(String status, String name) {}

    public record RecoveryCodeResponse(String code) {}

    public record RecoveryEmailRequest(String code, String email) {}
    public record RecoveryEmailResponse(String status) {}
    public record RecoveryResetRequest(String code, String token) {}
    public record RecoveryResetResponse(String status, String recoveryCode) {}

    public record ErrorBody(String code, String message) {}
}
