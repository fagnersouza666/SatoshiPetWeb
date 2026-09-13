package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.platform.AuthenticatedSession;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Superfície autenticada do pet da conta (snapshot compartilhado, fila e estatísticas).
 */
@Path("/api/v1/account/pet")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PetAccountResource {

    @Inject
    AuthenticatedSession authenticatedSession;

    @Inject
    PetPresentationService presentationService;

    @Inject
    PetStatsService statsService;

    /**
     * Snapshot compartilhado do pet da conta autenticada.
     *
     * <p>GET /api/v1/account/pet — mesmo bloco público + fila e stats da conta.</p>
     */
    @GET
    @Transactional
    public Response snapshot() {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        Account account = authenticatedSession.get().account;
        Optional<AccountAddressBinding> binding = AccountAddressBinding.findActivePrimary(account);
        if (binding.isEmpty()) {
            return petNotFound();
        }
        PetPublicSnapshot snapshot = PetPublicSnapshot.fromAddress(binding.get().address);
        if (snapshot.petName() == null) {
            return petNotFound();
        }
        return Response.ok(AccountPetResponse.of(
                snapshot,
                presentationService.presentationQueue(account),
                statsService.stats(account)
        )).build();
    }

    /**
     * Fila de comemorações ainda não apresentadas para a conta autenticada.
     *
     * <p>GET /api/v1/account/pet/presentation-queue</p>
     */
    @GET
    @Path("/presentation-queue")
    public Response presentationQueue() {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        Account account = authenticatedSession.get().account;
        return Response.ok(presentationService.presentationQueue(account)).build();
    }

    /**
     * Marca a fila atual como apresentada. Não altera reserva nem humor (CA-035).
     *
     * <p>POST /api/v1/account/pet/presentation/skip</p>
     */
    @POST
    @Path("/presentation/skip")
    public Response skipPresentation() {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        Account account = authenticatedSession.get().account;
        return Response.ok(presentationService.skip(account)).build();
    }

    /**
     * Estatísticas do pet da conta (idade desde o nascimento original, CC-21).
     *
     * <p>GET /api/v1/account/pet/stats</p>
     */
    @GET
    @Path("/stats")
    public Response stats() {
        if (!authenticatedSession.isAuthenticated()) {
            return unauthorized();
        }
        Account account = authenticatedSession.get().account;
        return Response.ok(statsService.stats(account)).build();
    }

    private Response unauthorized() {
        return Response.status(401).entity(new ErrorBody("unauthorized", "Autenticação necessária.")).build();
    }

    private Response petNotFound() {
        return Response.status(404).entity(new ErrorBody("pet_not_found", "Nenhum pet vinculado à conta.")).build();
    }

    public record ErrorBody(String code, String message) {
    }
}
