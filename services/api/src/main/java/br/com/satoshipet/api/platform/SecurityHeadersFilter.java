package br.com.satoshipet.api.platform;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Adiciona cabeçalhos de segurança em todas as respostas HTTP.
 *
 * <p>A política CSP é compatível com o Service Worker do Angular PWA
 * ({@code ngsw}): permite scripts e workers do mesmo origin, bloqueia
 * inline scripts e fontes externas não autorizadas.</p>
 */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    /**
     * Content-Security-Policy compatível com Angular ngsw.
     * Scripts inline são bloqueados; ngsw.js e worker são same-origin.
     */
    private static final String CSP =
            "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; "
            + "font-src 'self'; "
            + "media-src 'self'; "
            + "connect-src 'self' wss:; "
            + "worker-src 'self'; "
            + "manifest-src 'self'; "
            + "object-src 'none'; "
            + "frame-src 'none'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var headers = response.getHeaders();

        // Evita clickjacking mesmo em navegadores sem suporte a CSP.
        headers.putSingle("X-Frame-Options", "DENY");

        // Evita MIME sniffing.
        headers.putSingle("X-Content-Type-Options", "nosniff");

        // Força HTTPS por 1 ano; inclui subdomínios.
        headers.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Controle de informações de referência.
        headers.putSingle("Referrer-Policy", "strict-origin-when-cross-origin");

        // Desabilita funcionalidades sensíveis de navegador não necessárias.
        headers.putSingle("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=()");

        // CSP compatível com ngsw.
        headers.putSingle("Content-Security-Policy", CSP);
    }
}
