package br.com.satoshipet.api.pet;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Estatísticas do pet da conta (PET-17, CC-21). Sem petId.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PetStatsResponse(
        String bornAt,
        String ageHours,
        TimeInStateHours timeInStateHours,
        boolean reconstructedPeriod,
        boolean observedPeriod
) {
}
