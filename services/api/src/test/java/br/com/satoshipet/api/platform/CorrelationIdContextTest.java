package br.com.satoshipet.api.platform;

import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdContextTest {

    @AfterEach
    void limpaMdc() {
        MDC.remove(CorrelationIdContext.MDC_KEY);
    }

    @Test
    void preservaValorSeguroEValido() {
        assertEquals("request-123", CorrelationIdContext.fromHeader("request-123"));
        assertTrue(CorrelationIdContext.isValid("request-123"));
    }

    @Test
    void substituiValorMaiorQueColunaPorUuid() {
        String resolved = CorrelationIdContext.fromHeader("x".repeat(CorrelationIdContext.MAX_LENGTH + 1));

        assertNotNull(UUID.fromString(resolved));
        assertEquals(CorrelationIdContext.MAX_LENGTH, resolved.length());
    }

    @Test
    void escopoRestauraContextoAnterior() {
        MDC.put(CorrelationIdContext.MDC_KEY, "outer-1");

        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open("inner-2")) {
            assertEquals("inner-2", CorrelationIdContext.current());
        }

        assertEquals("outer-1", CorrelationIdContext.current());
    }

    @Test
    void escopoSemContextoAnteriorRemoveValorAoFechar() {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.openNew()) {
            assertNotNull(CorrelationIdContext.current());
        }

        assertNull(CorrelationIdContext.current());
    }
}
