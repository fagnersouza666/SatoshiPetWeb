package br.com.satoshipet.api.platform;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/**
 * Propaga ou gera o cabeçalho {@code X-Correlation-Id} em toda requisição.
 *
 * <p>Se a requisição já trouxer o cabeçalho, o valor é mantido e repropagado
 * na resposta. Caso contrário, um UUID aleatório é gerado e inserido.</p>
 */
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    /** Nome canônico do cabeçalho de correlação. */
    public static final String HEADER = "X-Correlation-Id";

    /** Chave para armazenar o id no contexto da requisição. */
    private static final String PROPERTY_KEY = "correlationId";

    @Override
    public void filter(ContainerRequestContext request) {
        String correlationId = request.getHeaderString(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        // Armazena para uso interno (OutboxService, logs, etc.).
        request.setProperty(PROPERTY_KEY, correlationId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String correlationId = (String) request.getProperty(PROPERTY_KEY);
        if (correlationId != null) {
            response.getHeaders().putSingle(HEADER, correlationId);
        }
    }
}
