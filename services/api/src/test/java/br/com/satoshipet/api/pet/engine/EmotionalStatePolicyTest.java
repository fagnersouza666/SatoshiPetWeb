package br.com.satoshipet.api.pet.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmotionalStatePolicyTest {

    private static final Instant DEPLETED_AT = Instant.parse("2026-03-01T12:00:00Z");

    @Test
    void reservaPositivaEAlimentado() {
        Instant now = DEPLETED_AT.plus(Duration.ofHours(80));

        assertEquals(EmotionalState.ALIMENTADO, EmotionalStatePolicy.of(hours("0.0000000001"), null, now));
        assertEquals(EmotionalState.ALIMENTADO, EmotionalStatePolicy.of(hours("24"), DEPLETED_AT, now));
    }

    @Test
    void esgotado0hEPensando() {
        assertEquals(EmotionalState.PENSANDO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, DEPLETED_AT));
        assertEquals(EmotionalState.PENSANDO, EmotionalStatePolicy.of(hours("0"), null, DEPLETED_AT));
    }

    @Test
    void esgotado24hEChateado() {
        Instant now = DEPLETED_AT.plus(Duration.ofHours(24));
        assertEquals(EmotionalState.CHATEADO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now));
        assertEquals(EmotionalState.CHATEADO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now.plus(Duration.ofHours(12))));
    }

    @Test
    void esgotado48hEFaminto() {
        Instant now = DEPLETED_AT.plus(Duration.ofHours(48));
        assertEquals(EmotionalState.FAMINTO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now));
        assertEquals(EmotionalState.FAMINTO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now.plus(Duration.ofHours(12))));
    }

    @Test
    void esgotado72hECritico() {
        Instant now = DEPLETED_AT.plus(Duration.ofHours(72));
        assertEquals(EmotionalState.CRITICO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now));
        assertEquals(EmotionalState.CRITICO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now.plus(Duration.ofHours(12))));
    }

    @Test
    void esgotado96hEHibernando() {
        Instant now = DEPLETED_AT.plus(Duration.ofHours(96));
        assertEquals(EmotionalState.HIBERNANDO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now));
        assertEquals(EmotionalState.HIBERNANDO, EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, now.plus(Duration.ofHours(24))));
    }

    @Test
    void logoAbaixoDeCadaFaixaPermaneceFaixaAnterior() {
        assertEquals(
                EmotionalState.PENSANDO,
                EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, DEPLETED_AT.plus(Duration.ofHours(23).plusMinutes(59))));
        assertEquals(
                EmotionalState.CHATEADO,
                EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, DEPLETED_AT.plus(Duration.ofHours(47).plusMinutes(59))));
        assertEquals(
                EmotionalState.FAMINTO,
                EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, DEPLETED_AT.plus(Duration.ofHours(71).plusMinutes(59))));
        assertEquals(
                EmotionalState.CRITICO,
                EmotionalStatePolicy.of(hours("0"), DEPLETED_AT, DEPLETED_AT.plus(Duration.ofHours(95).plusMinutes(59))));
    }

    private static BigDecimal hours(String value) {
        return new BigDecimal(value).setScale(10);
    }
}
