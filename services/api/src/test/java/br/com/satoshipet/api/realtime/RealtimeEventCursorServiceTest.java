package br.com.satoshipet.api.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeEventCursorServiceTest {

    private RealtimeEventCursorService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeEventCursorService();
    }

    @Test
    void currentCursorIniciaEmZeroSemEventos() {
        assertEquals("0", service.currentCursor("bc1qtest"));
    }

    @Test
    void nextCursorGeraSequenciaMonotonicamente() {
        String c1 = service.nextCursor("bc1qtest", "BITCOIN_TRANSACTION_OBSERVED", "{}");
        String c2 = service.nextCursor("bc1qtest", "BITCOIN_TRANSACTION_OBSERVED", "{}");

        long l1 = Long.parseLong(c1);
        long l2 = Long.parseLong(c2);

        assertTrue(l2 > l1, "Cursor deve crescer monotonicamente");
    }

    @Test
    void currentCursorRetornaUltimoEmitido() {
        service.nextCursor("bc1qa", "BITCOIN_TRANSACTION_OBSERVED", "{}");
        String last = service.nextCursor("bc1qa", "BITCOIN_TRANSACTION_OBSERVED", "{}");

        assertEquals(last, service.currentCursor("bc1qa"));
    }

    @Test
    void eventsAfterRetornaEventosPosteriores() {
        String c1 = service.nextCursor("bc1qb", "BITCOIN_TRANSACTION_OBSERVED", "data1");
        String c2 = service.nextCursor("bc1qb", "BITCOIN_TRANSACTION_OBSERVED", "data2");
        String c3 = service.nextCursor("bc1qb", "BITCOIN_TRANSACTION_OBSERVED", "data3");

        List<RealtimeEventCursorService.StoredEvent> events = service.eventsAfter("bc1qb", c1);

        assertEquals(2, events.size(), "Deve retornar apenas eventos após c1");
        assertEquals(c2, events.get(0).cursor());
        assertEquals(c3, events.get(1).cursor());
    }

    @Test
    void eventsAfterRetornaVazioParaCanalSemEventos() {
        List<RealtimeEventCursorService.StoredEvent> events = service.eventsAfter("bc1qdesconhecido", "0");
        assertTrue(events.isEmpty());
    }

    @Test
    void eventsAfterRetornaVazioParaCursorCursroMaisRecenteQueBuffer() {
        service.nextCursor("bc1qc", "BITCOIN_TRANSACTION_OBSERVED", "x");
        String last = service.nextCursor("bc1qc", "BITCOIN_TRANSACTION_OBSERVED", "y");

        List<RealtimeEventCursorService.StoredEvent> events = service.eventsAfter("bc1qc", last);
        assertTrue(events.isEmpty(), "Nenhum evento após o cursor mais recente");
    }

    @Test
    void canaisIndependentesNaoSeMisturam() {
        service.nextCursor("bc1qa", "BITCOIN_TRANSACTION_OBSERVED", "A");
        service.nextCursor("bc1qa", "BITCOIN_TRANSACTION_OBSERVED", "B");
        service.nextCursor("bc1qb", "BITCOIN_TRANSACTION_OBSERVED", "X");

        assertNotEquals(service.currentCursor("bc1qa"), service.currentCursor("bc1qb"));
    }

    @Test
    void ringBufferDescartaEventosMaisAntigosQuandoCheio() {
        String canal = "bc1qring";
        String firstCursor = null;
        // Insere mais do que RING_BUFFER_SIZE eventos
        for (int i = 0; i < RealtimeEventCursorService.RING_BUFFER_SIZE + 10; i++) {
            String c = service.nextCursor(canal, "BITCOIN_TRANSACTION_OBSERVED", "payload-" + i);
            if (i == 0) firstCursor = c;
        }
        // Eventos após o cursor inicial (que foi descartado) devem retornar <= RING_BUFFER_SIZE
        List<RealtimeEventCursorService.StoredEvent> events = service.eventsAfter(canal, firstCursor);
        assertTrue(events.size() <= RealtimeEventCursorService.RING_BUFFER_SIZE,
                "Ring buffer não deve exceder o tamanho máximo");
    }

    @Test
    void eventsAfterComCursorInvalidoRetornaVazio() {
        service.nextCursor("bc1qd", "BITCOIN_TRANSACTION_OBSERVED", "x");
        List<RealtimeEventCursorService.StoredEvent> events = service.eventsAfter("bc1qd", "cursor-invalido");
        assertTrue(events.isEmpty());
    }
}
