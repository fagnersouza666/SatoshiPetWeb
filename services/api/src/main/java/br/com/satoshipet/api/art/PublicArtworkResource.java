package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.Pet;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

/** Entrega pública de sprites aprovados (ART-10, CA-040). */
@Path("/api/v1/public/addresses/{address}/artwork")
public class PublicArtworkResource {

    @Inject
    ArtworkService artworkService;

    @GET
    @Path("/{version}/atlas.png")
    @Produces("image/png")
    public Response atlas(
            @PathParam("address") String address,
            @PathParam("version") int version
    ) {
        return serve(address, version, "atlas.png");
    }

    @GET
    @Path("/{version}/{pose}.png")
    @Produces("image/png")
    public Response pose(
            @PathParam("address") String address,
            @PathParam("version") int version,
            @PathParam("pose") String pose
    ) {
        return serve(address, version, pose + ".png");
    }

    private Response serve(String canonical, int version, String fileName) {
        Optional<Address> address = Address.findByCanonical(canonical);
        if (address.isEmpty()) {
            return Response.status(404).build();
        }
        Optional<Pet> pet = Pet.findByAddress(address.get());
        if (pet.isEmpty()) {
            return Response.status(404).build();
        }
        try {
            byte[] bytes = artworkService.readApprovedObject(pet.get(), version, fileName);
            return Response.ok(bytes).type("image/png").build();
        } catch (ArtworkOperationException e) {
            return Response.status(404).build();
        }
    }
}
