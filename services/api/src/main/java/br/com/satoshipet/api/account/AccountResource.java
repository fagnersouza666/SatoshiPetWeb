package br.com.satoshipet.api.account;

import br.com.satoshipet.api.isolation.AccountPrivateDataWipePort;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.platform.AuthenticatedSession;
import br.com.satoshipet.api.platform.SessionAuthFilter;
import jakarta.inject.Inject;
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
        return Response.ok(resp).build();
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
                    body.bitcoinAddress(),
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
        Pet pet = petOpt.get();

        // Somente o criador pode renomear (PRD §5)
        if (!pet.creatorAccount.id.equals(account.id)) {
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
        return Response.ok(new RecoveryCodeResponse(rawCode)).build();
    }

    /**
     * Usa um código de recuperação para criar nova sessão.
     *
     * <p>POST /api/v1/account/recovery/reset
     */
    @POST
    @Path("/recovery/reset")
    public Response recoveryReset(RecoveryResetRequest body, @Context ContainerRequestContext ctx) {
        if (body == null || body.code() == null || body.code().isBlank()) {
            return badRequest("code_required", "O código de recuperação é obrigatório.");
        }
        try {
            String userAgent = ctx.getHeaderString("User-Agent");
            String ip = extractIp(ctx);
            SessionService.SessionCreation creation = recoveryService.recover(
                    body.code(), Instant.now(), userAgent, ip
            );
            NewCookie sessionCookie = new NewCookie.Builder(SessionAuthFilter.SESSION_COOKIE)
                    .value(creation.rawSessionToken())
                    .path("/")
                    .httpOnly(true)
                    .maxAge(60 * 60 * 24 * 30)
                    .build();
            return Response.ok(new RecoveryResetResponse("ok"))
                    .cookie(sessionCookie)
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
        sessionService.revokeAll(account.id, Instant.now());
        wipePort.wipe(account.id);

        LOG.infof("Conta apagada (wipe): account=%s", account.id);

        NewCookie expiredCookie = new NewCookie.Builder(SessionAuthFilter.SESSION_COOKIE)
                .value("").path("/").httpOnly(true).maxAge(0).build();

        return Response.noContent().cookie(expiredCookie).build();
    }

    private Response unauthorized() {
        return Response.status(401).entity(new ErrorBody("unauthorized", "Autenticação necessária.")).build();
    }

    private Response badRequest(String code, String message) {
        return Response.status(400).entity(new ErrorBody(code, message)).build();
    }

    private String extractIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return "unknown";
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

    public record AddressChangeRequest(String bitcoinAddress) {}
    public record AddressChangeResponse(String status, String canonical) {}

    public record PetNameRequest(String name) {}
    public record PetNameResponse(String status, String name) {}

    public record RecoveryCodeResponse(String code) {}

    public record RecoveryResetRequest(String code) {}
    public record RecoveryResetResponse(String status) {}

    public record ErrorBody(String code, String message) {}
}
