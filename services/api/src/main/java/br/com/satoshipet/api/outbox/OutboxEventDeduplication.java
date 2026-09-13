package br.com.satoshipet.api.outbox;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registra as chaves dos eventos do outbox que já produziram efeito.
 *
 * <p>O registro é mantido no processo junto das projeções WebSocket do épico
 * FUND. A execução da ação é serializada por chave: uma reentrega concorrente
 * aguarda a primeira tentativa e é ignorada somente depois que ela termina com
 * sucesso. Se a ação falhar, a chave fica disponível para retry.</p>
 */
@ApplicationScoped
public class OutboxEventDeduplication {

    private final Set<UUID> appliedKeys = ConcurrentHashMap.newKeySet();
    /** Mantém a mesma trava durante a vida do registro para retries concorrentes. */
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    /**
     * Executa a ação uma única vez para a chave lógica informada.
     *
     * @return {@code true} quando a ação foi executada; {@code false} quando o
     *         evento já havia produzido efeito
     */
    public boolean executeOnce(UUID key, Runnable action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");

        if (appliedKeys.contains(key)) {
            return false;
        }

        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            if (appliedKeys.contains(key)) {
                return false;
            }

            action.run();
            appliedKeys.add(key);
            return true;
        }
    }
}
