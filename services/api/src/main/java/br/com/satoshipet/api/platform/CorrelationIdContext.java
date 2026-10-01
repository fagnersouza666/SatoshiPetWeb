package br.com.satoshipet.api.platform;

import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocketConnection;
import org.jboss.logmanager.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contexto de correlação compartilhado entre HTTP, jobs, eventos e logs.
 *
 * <p>O valor é deliberadamente opaco e limitado ao tamanho da coluna do
 * outbox. O contexto usa MDC para acompanhar o fluxo síncrono e sempre é
 * restaurado ao fim do escopo, evitando vazamento entre requisições.</p>
 */
public final class CorrelationIdContext {

    /** Nome do header HTTP e do campo operacional do contexto. */
    public static final String HEADER = "X-Correlation-Id";

    /** Chave usada pelo formato de log do Quarkus. */
    public static final String MDC_KEY = "correlationId";

    /** Limite definido pelo contrato de persistência do outbox. */
    public static final int MAX_LENGTH = 36;

    private static final Pattern SAFE_VALUE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0," + (MAX_LENGTH - 1) + "}");
    private static final UserData.TypedKey<String> WEBSOCKET_KEY =
            UserData.TypedKey.forString("satoshi-pet.correlation-id");

    private CorrelationIdContext() {
    }

    /** Retorna o ID atual do fluxo, se houver um valor válido no MDC. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return isValid(value) ? value : null;
    }

    /** Retorna o ID atual ou cria um para uma operação sem contexto. */
    public static String currentOrNew() {
        String current = current();
        return current != null ? current : newId();
    }

    /** Gera um ID opaco compatível com o limite do outbox. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Aceita um valor explícito ou reutiliza o MDC; se nenhum for válido,
     * gera um novo ID.
     */
    public static String resolve(String requested) {
        if (isValid(requested)) {
            return requested;
        }
        return currentOrNew();
    }

    /** Resolve o header de uma nova requisição sem reutilizar um contexto externo. */
    public static String fromHeader(String requested) {
        return isValid(requested) ? requested : newId();
    }

    /** Inicia um escopo com um novo ID, útil para jobs independentes. */
    public static Scope openNew() {
        return open(newId());
    }

    /** Inicia um escopo e restaura o MDC anterior ao fechá-lo. */
    public static Scope open(String requested) {
        String correlationId = resolve(requested);
        String previous = MDC.put(MDC_KEY, correlationId);
        return () -> restore(previous);
    }

    /**
     * Abre o escopo de uma conexão WebSocket, persistindo o ID da abertura
     * para que mensagens posteriores da mesma conexão mantenham a correlação.
     */
    public static Scope open(WebSocketConnection connection) {
        String correlationId = connection.userData().get(WEBSOCKET_KEY);
        if (!isValid(correlationId)) {
            correlationId = fromHeader(connection.handshakeRequest().header(HEADER));
            connection.userData().put(WEBSOCKET_KEY, correlationId);
        }
        return open(correlationId);
    }

    /** Indica se o valor é seguro e cabe no campo persistido. */
    public static boolean isValid(String value) {
        return value != null && SAFE_VALUE.matcher(value).matches();
    }

    private static void restore(String previous) {
        if (previous == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previous);
        }
    }

    /** Escopo de correlação que restaura o MDC ao ser fechado. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
