package br.com.satoshipet.api.platform;

import br.com.satoshipet.api.account.Session;
import jakarta.enterprise.context.RequestScoped;

/**
 * Contexto de sessão autenticada para o request atual.
 *
 * <p>Populado pelo {@link SessionAuthFilter} após validação do cookie
 * {@code sp_session}. Injetado pelos resources que exigem autenticação.</p>
 */
@RequestScoped
public class AuthenticatedSession {

    private Session session;

    /** Define a sessão autenticada do request. */
    public void set(Session session) {
        this.session = session;
    }

    /** Retorna a sessão autenticada, ou {@code null} se não autenticado. */
    public Session get() {
        return session;
    }

    /** Verifica se o request atual possui sessão autenticada válida. */
    public boolean isAuthenticated() {
        return session != null;
    }
}
