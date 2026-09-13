package br.com.satoshipet.api.pet.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Faixas emocionais a partir da reserva restante e do instante de esgotamento (CA-019).
 *
 * <p>Fronteiras inclusivas no início da faixa: 24h → CHATEADO, 48h → FAMINTO,
 * 72h → CRITICO, 96h → HIBERNANDO.</p>
 */
public final class EmotionalStatePolicy {

    private static final Duration PENSANDO_UNTIL = Duration.ofHours(24);
    private static final Duration CHATEADO_UNTIL = Duration.ofHours(48);
    private static final Duration FAMINTO_UNTIL = Duration.ofHours(72);
    private static final Duration CRITICO_UNTIL = Duration.ofHours(96);

    private EmotionalStatePolicy() {
    }

    /**
     * Resolve o estado emocional no instante {@code now}.
     *
     * @param remainingHours horas restantes de reserva
     * @param depletedAt     instante de esgotamento, ou nulo se ainda não gravado
     * @param now            instante da avaliação
     * @return estado emocional (nunca OVO)
     */
    public static EmotionalState of(BigDecimal remainingHours, Instant depletedAt, Instant now) {
        Objects.requireNonNull(remainingHours, "remainingHours");
        Objects.requireNonNull(now, "now");

        if (remainingHours.compareTo(BigDecimal.ZERO) > 0) {
            return EmotionalState.ALIMENTADO;
        }

        Instant effectiveDepletedAt = depletedAt == null ? now : depletedAt;
        Duration elapsed = Duration.between(effectiveDepletedAt, now);
        if (elapsed.isNegative()) {
            elapsed = Duration.ZERO;
        }

        if (elapsed.compareTo(CRITICO_UNTIL) >= 0) {
            return EmotionalState.HIBERNANDO;
        }
        if (elapsed.compareTo(FAMINTO_UNTIL) >= 0) {
            return EmotionalState.CRITICO;
        }
        if (elapsed.compareTo(CHATEADO_UNTIL) >= 0) {
            return EmotionalState.FAMINTO;
        }
        if (elapsed.compareTo(PENSANDO_UNTIL) >= 0) {
            return EmotionalState.CHATEADO;
        }
        return EmotionalState.PENSANDO;
    }
}
