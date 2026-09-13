package br.com.satoshipet.api.pet;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tempo acumulado em cada estado emocional (CC-10: string decimal, nunca double).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeInStateHours(
        String ALIMENTADO,
        String PENSANDO,
        String CHATEADO,
        String FAMINTO,
        String CRITICO,
        String HIBERNANDO
) {
    static TimeInStateHours zeros() {
        String zero = "0.0000000000";
        return new TimeInStateHours(zero, zero, zero, zero, zero, zero);
    }
}
