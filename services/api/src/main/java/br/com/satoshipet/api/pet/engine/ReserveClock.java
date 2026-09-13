package br.com.satoshipet.api.pet.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Consome a reserva pelo tempo decorrido desde a última avaliação (CA-018).
 *
 * <p>Não gera dívida de fome: reserva esgotada permanece em zero. O instante
 * de esgotamento só é gravado na primeira vez em que a reserva atinge zero.</p>
 */
public final class ReserveClock {

    private static final BigDecimal NANOS_PER_HOUR = new BigDecimal("3600000000000");
    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);
    private static final RoundingMode ROUNDING = RoundingMode.DOWN;

    private ReserveClock() {
    }

    /**
     * Resultado do consumo temporal da reserva.
     *
     * @param remainingHours horas restantes (nunca negativas, escala 10)
     * @param depletedAt     instante em que a reserva chegou a zero, ou nulo
     * @param evaluatedAt    instante {@code now} usado nesta avaliação
     */
    public record Consumption(BigDecimal remainingHours, Instant depletedAt, Instant evaluatedAt) {
    }

    /**
     * Consome a reserva pelo intervalo entre {@code lastEvaluatedAt} e {@code now}.
     *
     * @param reserveHours     reserva imediatamente antes do consumo
     * @param lastEvaluatedAt  última avaliação persistida
     * @param depletedAt       instante de esgotamento já conhecido, ou nulo
     * @param now              instante atual (injetado pelo chamador)
     * @return reserva restante, depletedAt atualizado e evaluatedAt = now
     */
    public static Consumption consume(
            BigDecimal reserveHours,
            Instant lastEvaluatedAt,
            Instant depletedAt,
            Instant now) {
        Objects.requireNonNull(reserveHours, "reserveHours");
        Objects.requireNonNull(lastEvaluatedAt, "lastEvaluatedAt");
        Objects.requireNonNull(now, "now");

        BigDecimal clampedReserve = reserveHours.max(BigDecimal.ZERO).setScale(ReserveMath.SCALE, ROUNDING);
        BigDecimal elapsedHours = elapsedHours(lastEvaluatedAt, now);
        BigDecimal remaining = clampedReserve.subtract(elapsedHours);

        if (remaining.signum() > 0) {
            return new Consumption(remaining.setScale(ReserveMath.SCALE, ROUNDING), null, now);
        }

        Instant markedDepletedAt = depletedAt;
        if (markedDepletedAt == null) {
            markedDepletedAt = lastEvaluatedAt.plus(hoursToDuration(clampedReserve));
        }
        return new Consumption(BigDecimal.ZERO.setScale(ReserveMath.SCALE), markedDepletedAt, now);
    }

    private static BigDecimal elapsedHours(Instant lastEvaluatedAt, Instant now) {
        Duration elapsed = Duration.between(lastEvaluatedAt, now);
        if (elapsed.isNegative() || elapsed.isZero()) {
            return BigDecimal.ZERO.setScale(ReserveMath.SCALE);
        }
        BigDecimal nanos = BigDecimal.valueOf(elapsed.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigDecimal.valueOf(elapsed.getNano()));
        return nanos.divide(NANOS_PER_HOUR, ReserveMath.SCALE, ROUNDING);
    }

    private static Duration hoursToDuration(BigDecimal hours) {
        if (hours.signum() <= 0) {
            return Duration.ZERO;
        }
        BigDecimal nanos = hours.multiply(NANOS_PER_HOUR).setScale(0, ROUNDING);
        if (nanos.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return Duration.ofNanos(Long.MAX_VALUE);
        }
        return Duration.ofNanos(nanos.longValueExact());
    }
}
