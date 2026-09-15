package br.com.satoshipet.api.art;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contexto congelado no primeiro disparo de geração (PRD §8.1, ART-01).
 * Nunca inclui prompt privado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FrozenGenerationContext(
        String addressCanonical,
        String timezone,
        String locationLabel,
        String weatherSummary,
        String dayPeriod,
        String frozenAt
) {
}
