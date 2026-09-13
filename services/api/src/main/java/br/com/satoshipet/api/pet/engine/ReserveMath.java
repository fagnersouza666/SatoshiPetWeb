package br.com.satoshipet.api.pet.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Matemática pura da reserva de alimentação (CA-015, CA-016, CC-10).
 *
 * <p>Nunca usa ponto flutuante binário. Horas acrescentadas:
 * {@code 24 × satsRecebidos / porcaoReferenciaSats}, com escala 10 e
 * {@link RoundingMode#DOWN}. O teto da reserva é 168 horas.</p>
 */
public final class ReserveMath {

    public static final int MAX_RESERVE_HOURS = 168;
    public static final int SCALE = 10;
    public static final String RULE_VERSION = "pet-feeding-v1";

    private static final BigDecimal HOURS_PER_PORTION = BigDecimal.valueOf(24L);
    private static final BigDecimal MAX_RESERVE = BigDecimal.valueOf(MAX_RESERVE_HOURS);
    private static final RoundingMode ROUNDING = RoundingMode.DOWN;

    private ReserveMath() {
    }

    /**
     * Horas acrescentadas pela porção recebida.
     *
     * @param receivedSats sats do recebimento on-chain (>= 0)
     * @param portionSats  porção de referência em sats (> 0)
     * @return horas com escala 10 e arredondamento para baixo
     */
    public static BigDecimal hoursAdded(long receivedSats, long portionSats) {
        if (portionSats <= 0L) {
            throw new IllegalArgumentException("porcaoReferenciaSats deve ser maior que zero");
        }
        if (receivedSats < 0L) {
            throw new IllegalArgumentException("satsRecebidos não pode ser negativo");
        }
        return HOURS_PER_PORTION
                .multiply(BigDecimal.valueOf(receivedSats))
                .divide(BigDecimal.valueOf(portionSats), SCALE, ROUNDING);
    }

    /**
     * Aplica o teto de 168h. Reserva negativa é tratada como zero (sem dívida).
     *
     * @param reserveBefore reserva atual em horas
     * @param hoursAdded    horas a acrescentar
     * @return reserva após o cap, nunca negativa, escala 10
     */
    public static BigDecimal applyCap(BigDecimal reserveBefore, BigDecimal hoursAdded) {
        Objects.requireNonNull(reserveBefore, "reserveBefore");
        Objects.requireNonNull(hoursAdded, "hoursAdded");
        BigDecimal before = reserveBefore.max(BigDecimal.ZERO);
        BigDecimal after = before.add(hoursAdded);
        if (after.signum() < 0) {
            after = BigDecimal.ZERO;
        }
        return after.min(MAX_RESERVE).setScale(SCALE, ROUNDING);
    }
}
