package br.com.satoshipet.api.pet;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Item da fila de apresentação da conta (CA-009: sem petId/accountId/email).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresentationQueueItem(
        String eventId,
        String eventType,
        String occurredAt,
        Long amountSats
) {
}
