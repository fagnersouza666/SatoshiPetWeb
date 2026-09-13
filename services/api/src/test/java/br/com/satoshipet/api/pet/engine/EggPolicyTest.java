package br.com.satoshipet.api.pet.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EggPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");

    @Test
    void saldoConfirmadoPositivoZeraCarencia() {
        Instant started = NOW.minus(Duration.ofHours(10));

        assertNull(EggPolicy.nextZeroBalanceSince(started, 1L, NOW));
        assertNull(EggPolicy.nextZeroBalanceSince(null, 5_000L, NOW));
    }

    @Test
    void saldoConfirmadoZeroIniciaCarenciaQuandoAindaNula() {
        assertEquals(NOW, EggPolicy.nextZeroBalanceSince(null, 0L, NOW));
    }

    @Test
    void saldoConfirmadoZeroMantemInicioJaGravado() {
        Instant started = NOW.minus(Duration.ofHours(3));

        assertEquals(started, EggPolicy.nextZeroBalanceSince(started, 0L, NOW));
    }

    @Test
    void carenciaNulaNuncaElapsed() {
        assertFalse(EggPolicy.graceElapsed(null, NOW));
        assertFalse(EggPolicy.graceElapsed(null, NOW.plus(Duration.ofHours(48))));
    }

    @Test
    void carenciaDe24hEInclusivaNoLimite() {
        Instant since = NOW;

        assertFalse(EggPolicy.graceElapsed(since, since.plus(Duration.ofHours(23))));
        assertFalse(EggPolicy.graceElapsed(since, since.plus(Duration.ofHours(24)).minusNanos(1)));
        assertTrue(EggPolicy.graceElapsed(since, since.plus(Duration.ofHours(24))));
        assertTrue(EggPolicy.graceElapsed(since, since.plus(Duration.ofHours(25))));
    }

    @Test
    void soApareceComSaldoConfirmadoPositivo() {
        assertFalse(EggPolicy.canAppear(0L));
        assertFalse(EggPolicy.canAppear(-1L));
        assertTrue(EggPolicy.canAppear(1L));
        assertTrue(EggPolicy.canAppear(5_000L));
    }

    @Test
    void ovoImediatoSoQuandoNasceuPerdeuFundamentoESaldoZero() {
        assertTrue(EggPolicy.immediateEggOnLostBirthFoundation(true, false, 0L));
        assertFalse(EggPolicy.immediateEggOnLostBirthFoundation(false, false, 0L));
        assertFalse(EggPolicy.immediateEggOnLostBirthFoundation(true, true, 0L));
        assertFalse(EggPolicy.immediateEggOnLostBirthFoundation(true, false, 1L));
    }
}
