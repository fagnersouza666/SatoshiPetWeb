package br.com.satoshipet.api.events;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Catálogo único dos eventos de domínio definidos no PRD §16.3.
 *
 * <p>O nome textual do enum é o valor persistido em {@code event_type} e
 * transmitido por REST, WebSocket e relatórios. Não adicionar tipos aqui sem
 * antes atualizar o catálogo versionado e o contrato do contexto responsável
 * pelo payload.</p>
 */
public enum DomainEventType {
    BITCOIN_TRANSACTION_OBSERVED,
    BITCOIN_TRANSACTION_CONFIRMED,
    BITCOIN_TRANSACTION_REPLACED,
    BITCOIN_TRANSACTION_DROPPED,
    BITCOIN_CHAIN_REORG,
    BITCOIN_BALANCE_RECONCILED,
    PET_FEEDING_APPLIED,
    PET_FEEDING_REVISED,
    PET_FEEDING_INVALIDATED,
    PET_ARTWORK_READY,
    PET_BORN,
    PET_RETURNED_TO_EGG,
    PET_REAPPEARED,
    PET_STATE_CHANGED,
    DCA_RECOMMENDATION_GENERATED,
    DCA_RECOMMENDATION_EXPIRED,
    PURCHASE_REPORTED,
    PURCHASE_CORRECTED,
    PURCHASE_DELETED,
    LOCATION_CHANGE_SCHEDULED,
    LOCATION_CHANGE_APPLIED;

    private static final Set<String> CATALOG = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.stream(values()).map(DomainEventType::value).toList())
    );

    /** Retorna o nome canônico usado nas fronteiras da aplicação. */
    public String value() {
        return name();
    }

    /**
     * Converte um nome recebido de uma fronteira para o tipo catalogado.
     *
     * @throws NullPointerException se o valor for nulo
     * @throws IllegalArgumentException se o valor não estiver no catálogo
     */
    public static DomainEventType fromValue(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Tipo de evento fora do catálogo: " + value, exception);
        }
    }

    /** Retorna os nomes canônicos, preservando a ordem do PRD §16.3. */
    public static Set<String> catalog() {
        return CATALOG;
    }
}
