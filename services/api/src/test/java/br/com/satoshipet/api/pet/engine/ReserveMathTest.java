package br.com.satoshipet.api.pet.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReserveMathTest {

    private static final long PORTION_SATS = 20_000L;

    @Test
    void porcao20000Recebe5000Acrescenta6Horas() {
        assertEquals(hours("6"), ReserveMath.hoursAdded(5_000L, PORTION_SATS));
    }

    @Test
    void porcao20000Recebe10000Acrescenta12Horas() {
        assertEquals(hours("12"), ReserveMath.hoursAdded(10_000L, PORTION_SATS));
    }

    @Test
    void porcao20000Recebe20000Acrescenta24Horas() {
        assertEquals(hours("24"), ReserveMath.hoursAdded(20_000L, PORTION_SATS));
    }

    @Test
    void porcao20000Recebe40000Acrescenta48Horas() {
        assertEquals(hours("48"), ReserveMath.hoursAdded(40_000L, PORTION_SATS));
    }

    @Test
    void reserva144hMais72hCapaEm168h() {
        BigDecimal hoursAdded = ReserveMath.hoursAdded(60_000L, PORTION_SATS);
        assertEquals(hours("72"), hoursAdded);
        assertEquals(hours("168"), ReserveMath.applyCap(hours("144"), hoursAdded));
    }

    @Test
    void reservaJa168hNaoUltrapassa() {
        assertEquals(hours("168"), ReserveMath.applyCap(hours("168"), hours("72")));
    }

    @Test
    void satsZeroNaoAcrescenta() {
        assertEquals(hours("0"), ReserveMath.hoursAdded(0L, PORTION_SATS));
    }

    @Test
    void porcaoInvalidaLanca() {
        assertThrows(IllegalArgumentException.class, () -> ReserveMath.hoursAdded(5_000L, 0L));
        assertThrows(IllegalArgumentException.class, () -> ReserveMath.hoursAdded(5_000L, -1L));
    }

    @Test
    void recebimentoNegativoLanca() {
        assertThrows(IllegalArgumentException.class, () -> ReserveMath.hoursAdded(-1L, PORTION_SATS));
    }

    private static BigDecimal hours(String value) {
        return new BigDecimal(value).setScale(10);
    }
}
