package br.com.satoshipet.api.realtime;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Atribui cursores monotônicos por canal de endereço e mantém um ring buffer
 * em memória para suportar reconexões (replay de eventos recentes).
 *
 * <p>O cursor é uma string que representa um contador de 64 bits. O cliente
 * armazena o último cursor recebido e o envia no frame {@code RECONNECT}.
 * O servidor responde com os eventos posteriores ao cursor.</p>
 *
 * <p>Implementação em memória — suficiente para o épico FUND.
 * Em produção com múltiplas réplicas, migrar para Redis XADD/XRANGE.</p>
 */
@ApplicationScoped
public class RealtimeEventCursorService {

    private static final Logger LOG = Logger.getLogger(RealtimeEventCursorService.class);

    /** Tamanho máximo do ring buffer por endereço (eventos recentes). */
    static final int RING_BUFFER_SIZE = 200;

    /** Sequência global de cursores para garantir monotonicidade. */
    private final AtomicLong globalSequence = new AtomicLong(0);

    /** Buffer de eventos por endereço canônico. */
    private final Map<String, Deque<StoredEvent>> buffers = new ConcurrentHashMap<>();

    /**
     * Gera e atribui o próximo cursor para um endereço. Armazena o evento
     * no ring buffer para permitir replay em reconexões.
     *
     * @param canonical endereço Bitcoin canônico (chave do canal)
     * @param eventType tipo do evento (catálogo único PRD §16.3)
     * @param data      dados do evento a armazenar
     * @return cursor atribuído ao evento (string numérica monotônica)
     */
    public String nextCursor(String canonical, String eventType, Object data) {
        String cursor = String.valueOf(globalSequence.incrementAndGet());

        Deque<StoredEvent> buffer = buffers.computeIfAbsent(canonical, k -> new ArrayDeque<>(RING_BUFFER_SIZE));
        synchronized (buffer) {
            if (buffer.size() >= RING_BUFFER_SIZE) {
                buffer.removeFirst(); // descarta o mais antigo
            }
            buffer.addLast(new StoredEvent(cursor, eventType, data));
        }

        LOG.tracef("Cursor %s atribuído para address=%s eventType=%s", cursor, canonical, eventType);
        return cursor;
    }

    /**
     * Retorna o cursor atual (último emitido para o endereço) sem avançar.
     * Retorna "0" se nenhum evento foi emitido ainda para o endereço.
     *
     * @param canonical endereço Bitcoin canônico
     * @return último cursor emitido, ou "0"
     */
    public String currentCursor(String canonical) {
        Deque<StoredEvent> buffer = buffers.get(canonical);
        if (buffer == null) {
            return "0";
        }
        synchronized (buffer) {
            StoredEvent last = buffer.peekLast();
            return last != null ? last.cursor() : "0";
        }
    }

    /**
     * Retorna os eventos posteriores ao cursor informado (replay para reconexão).
     * Retorna lista vazia se o cursor for mais recente que o buffer ou não
     * houver eventos registrados.
     *
     * @param canonical    endereço Bitcoin canônico
     * @param afterCursor  cursor a partir do qual recuperar (exclusive)
     * @return eventos posteriores ao cursor, ordenados do mais antigo ao mais recente
     */
    public List<StoredEvent> eventsAfter(String canonical, String afterCursor) {
        Deque<StoredEvent> buffer = buffers.get(canonical);
        if (buffer == null) {
            return List.of();
        }

        long after;
        try {
            after = Long.parseLong(afterCursor);
        } catch (NumberFormatException e) {
            LOG.warnf("Cursor inválido '%s' para address=%s — retornando lista vazia", afterCursor, canonical);
            return List.of();
        }

        synchronized (buffer) {
            List<StoredEvent> result = new ArrayList<>();
            for (StoredEvent event : buffer) {
                try {
                    if (Long.parseLong(event.cursor()) > after) {
                        result.add(event);
                    }
                } catch (NumberFormatException ignore) {
                    // cursor malformado no buffer — ignorar
                }
            }
            return List.copyOf(result);
        }
    }

    /**
     * Evento armazenado no ring buffer com cursor, tipo e dados.
     *
     * @param cursor    cursor monotônico do evento
     * @param eventType tipo do evento (catálogo único)
     * @param data      dados do evento (objeto serializável)
     */
    public record StoredEvent(String cursor, String eventType, Object data) {}
}
