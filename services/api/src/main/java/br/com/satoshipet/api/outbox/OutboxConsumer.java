package br.com.satoshipet.api.outbox;

/**
 * Contrato para componentes que consomem eventos do outbox transacional.
 *
 * <p>Implemente esta interface e anote a classe com {@code @ApplicationScoped}
 * para que o {@link OutboxPublisher} a descubra e despache os eventos
 * relevantes automaticamente.</p>
 */
public interface OutboxConsumer {

    /**
     * Processa o evento fornecido.
     * A implementação deve ser idempotente: o publisher pode chamar mais de uma vez
     * em caso de falha transitória.
     *
     * @param event evento pendente a ser processado
     */
    void consume(OutboxEvent event);

    /**
     * Indica se este consumidor aceita o tipo de evento informado.
     *
     * @param eventType tipo do evento (ex: "BITCOIN_TRANSACTION_OBSERVED")
     * @return {@code true} se o consumidor deve receber o evento
     */
    boolean supports(String eventType);
}
