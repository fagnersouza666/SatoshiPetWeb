package br.com.satoshipet.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Endpoint mínimo para validar o scaffold da API. */
@Path("/api/v1/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Olá do Satoshi Pet Web";
    }
}
