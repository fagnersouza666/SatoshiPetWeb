package br.com.satoshipet.api.pet;

import java.util.List;

/** Resposta explícita da fila de comemorações por conta. */
public record PresentationQueueResponse(List<PresentationQueueItem> items) {
}
