package br.com.satoshipet.api.pet.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReserveClockTest {

    private static final Instant T0 = Instant.parse("2026-01-15T08:00:00Z");

    @Test
    void semTempoDecorridoNaoConsome() {
        ReserveClock.Consumption consumption = ReserveClock.consume(hours("24"), T0, null, T0);

        assertEquals(hours("24"), consumption.remainingHours());
        assertNull(consumption.depletedAt());
        assertEquals(T0, consumption.evaluatedAt());
    }

    @Test
    void consomeHorasProporcionalmente() {
        Instant now = T0.plus(Duration.ofHours(10));

        ReserveClock.Consumption consumption = ReserveClock.consume(hours("24"), T0, null, now);

        assertEquals(hours("14"), consumption.remainingHours());
        assertNull(consumption.depletedAt());
        assertEquals(now, consumption.evaluatedAt());
    }

    @Test
    void esgotadaNaoGeraDividaDeFome() {
        Instant depletedAt = T0.minus(Duration.ofDays(3));
        Instant now = T0.plus(Duration.ofDays(2));

        ReserveClock.Consumption consumption = ReserveClock.consume(hours("0"), T0, depletedAt, now);

        assertEquals(hours("0"), consumption.remainingHours());
        assertEquals(depletedAt, consumption.depletedAt());

        BigDecimal afterFeeding = ReserveMath.applyCap(consumption.remainingHours(), hours("24"));
        assertEquals(hours("24"), afterFeeding);
    }

    @Test
    void preservaDepletedAtQuandoJaEsgotada() {
        Instant depletedAt = Instant.parse("2026-01-10T08:00:00Z");
        Instant now = T0.plus(Duration.ofHours(6));

        ReserveClock.Consumption consumption = ReserveClock.consume(hours("0"), T0, depletedAt, now);

        assertEquals(hours("0"), consumption.remainingHours());
        assertEquals(depletedAt, consumption.depletedAt());
        assertEquals(now, consumption.evaluatedAt());
    }

    @Test
    void marcaDepletedAtQuandoAtingeZero() {
        Instant now = T0.plus(Duration.ofHours(15));

        ReserveClock.Consumption consumption = ReserveClock.consume(hours("10"), T0, null, now);

        assertEquals(hours("0"), consumption.remainingHours());
        assertEquals(T0.plus(Duration.ofHours(10)), consumption.depletedAt());
        assertEquals(now, consumption.evaluatedAt());
    }

    @Test
    void nowAntesDeLastEvaluatedNaoRetrocede() {
        Instant now = T0.minus(Duration.ofHours(3));

        ReserveClock.Consumption consumption = ReserveClock.consume(hours("10"), T0, null, now);

        assertEquals(hours("10"), consumption.remainingHours());
        assertNull(consumption.depletedAt());
        assertEquals(now, consumption.evaluatedAt());
    }

    private static BigDecimal hours(String value) {
        return new BigDecimal(value).setScale(10);
    }
}
