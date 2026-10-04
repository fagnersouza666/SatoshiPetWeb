package br.com.satoshipet.api.platform;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.Context;

/** IP resolvido pelo transporte e pelos proxies explicitamente confiáveis do Quarkus. */
@RequestScoped
public class ClientAddress {
    @Context HttpServerRequest request;

    public String value() {
        if (request == null || request.remoteAddress() == null) return "unknown";
        String host = request.remoteAddress().host();
        return host == null || host.isBlank() ? "unknown" : host;
    }
}
