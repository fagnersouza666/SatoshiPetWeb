package br.com.satoshipet.api.platform;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

/**
 * Propaga ou gera o cabeçalho {@code X-Correlation-Id} em toda requisição.
 *
 * <p>Se a requisição já trouxer um valor seguro de até 36 caracteres, ele é
 * mantido e repropagado na resposta. Caso contrário, um UUID aleatório é
 * gerado e inserido.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    /** Nome canônico do cabeçalho de correlação. */
    public static final String HEADER = CorrelationIdContext.HEADER;

    /** Chave para armazenar o id no contexto da requisição. */
    private static final String PROPERTY_KEY = CorrelationIdFilter.class.getName() + ".value";
    private static final String SCOPE_KEY = CorrelationIdFilter.class.getName() + ".scope";

    @Override
    public void filter(ContainerRequestContext request) {
        String correlationId = CorrelationIdContext.fromHeader(request.getHeaderString(HEADER));
        // Armazena para uso interno (OutboxService, logs, etc.) e limpeza.
        request.setProperty(PROPERTY_KEY, correlationId);
        request.setProperty(SCOPE_KEY, CorrelationIdContext.open(correlationId));
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String correlationId = (String) request.getProperty(PROPERTY_KEY);
        if (correlationId != null) {
            response.getHeaders().putSingle(HEADER, correlationId);
        }
        Object scope = request.getProperty(SCOPE_KEY);
        if (scope instanceof CorrelationIdContext.Scope correlationScope) {
            correlationScope.close();
        }
    }
}
