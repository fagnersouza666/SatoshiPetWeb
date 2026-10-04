package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.outbox.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RealtimeEventCursorServiceTest {
    @Inject RealtimeEventCursorService service;
    @Inject EntityManager entityManager;
    @Inject ObjectMapper objectMapper;

    @Test
    @TestTransaction
    void cursorVemDoBancoTambemEmOutraInstancia() {
        String channel = channel();
        assertEquals("0", service.currentCursor(channel));
        OutboxEvent event = event(channel);
        String first = service.record(event, channel);
        RealtimeEventCursorService replica = new RealtimeEventCursorService(entityManager, objectMapper);
        assertEquals(first, replica.currentCursor(channel));
        assertEquals(first, replica.record(event, channel));
        assertEquals(1, replica.replayAfter(channel, "0").events().size());
    }

    @Test
    @TestTransaction
    void replayTemOrdemCrescenteEIsolaCanal() {
        String channel = channel();
        String first = service.record(event(channel), channel);
        String otherChannel = channel();
        service.record(event(otherChannel), otherChannel);
        String second = service.record(event(channel), channel);
        String third = service.record(event(channel), channel);
        var replay = service.replayAfter(channel, first);
        assertFalse(replay.snapshotRequired());
        assertEquals(java.util.List.of(second, third), replay.events().stream().map(RealtimeEventCursorService.StoredEvent::cursor).toList());
        assertEquals(third, replay.currentCursor());
    }

    @Test
    @TestTransaction
    void janelaExpiradaExigeSnapshotAtualSemReplayParcial() {
        String channel = channel();
        String first = service.record(event(channel), channel);
        for (int i = 0; i < 210; i++) service.record(event(channel), channel);
        var replay = service.replayAfter(channel, first);
        assertTrue(replay.snapshotRequired());
        assertTrue(replay.events().isEmpty());
        assertEquals(service.currentCursor(channel), replay.currentCursor());
        // Reentrega fora da janela continua idempotente porque o recibo é durável.
        var oldest = entityManager.createQuery("SELECT r FROM RealtimeEventReceipt r WHERE r.canonical=:channel ORDER BY r.cursor", RealtimeEventReceipt.class)
                .setParameter("channel", channel).setMaxResults(1).getSingleResult();
        String current = service.currentCursor(channel);
        assertEquals(first, service.record(oldest.event, channel));
        assertEquals(current, service.currentCursor(channel));
    }

    @Test
    @TestTransaction
    void cursoresInvalidosOuAdiantadosNaoSaoRefletidosComoEstadoServidor() {
        String channel = channel();
        String current = service.record(event(channel), channel);
        for (String requested : java.util.List.of("-1", "invalid", "9223372036854775807")) {
            var replay = service.replayAfter(channel, requested);
            assertTrue(replay.snapshotRequired());
            assertEquals(current, replay.currentCursor());
            assertTrue(replay.events().isEmpty());
        }
        assertTrue(service.replayAfter(channel(), "12345").snapshotRequired());
        assertEquals("0", service.replayAfter(channel(), "12345").currentCursor());
    }

    @Test
    void rollbackNaoPublicaReciboNemCursorFantasma() {
        String channel = channel();
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            service.record(event(channel), channel);
            throw new IllegalStateException("rollback controlado");
        }));
        assertEquals("0", service.currentCursor(channel));
    }

    @Test
    void cursoresNaoUltrapassamTransacaoAnteriorAindaNaoConfirmada() throws Exception {
        String firstChannel = channel();
        String secondChannel = channel();
        CountDownLatch firstRecorded = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> QuarkusTransaction.requiringNew().call(() -> {
            String cursor = service.record(event(firstChannel), firstChannel);
            firstRecorded.countDown();
            assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
            return cursor;
        }));
        assertTrue(firstRecorded.await(10, TimeUnit.SECONDS));
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> QuarkusTransaction.requiringNew().call(() -> {
            secondStarted.countDown();
            return service.record(event(secondChannel), secondChannel);
        }));
        try {
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
        } finally {
            releaseFirst.countDown();
        }
        assertTrue(Long.parseLong(second.get(10, TimeUnit.SECONDS)) > Long.parseLong(first.get(10, TimeUnit.SECONDS)));
    }

    @Test
    void duasTransacoesReentregamMesmoEventoSemDuplicarProjecao() throws Exception {
        String channel = channel();
        UUID eventId = QuarkusTransaction.requiringNew().call(() -> event(channel).id);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> recordCommitted(eventId, channel));
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> recordCommitted(eventId, channel));
        assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertEquals(1, service.replayAfter(channel, "0").events().size());
    }

    private String recordCommitted(UUID eventId, String channel) {
        return QuarkusTransaction.requiringNew().call(() -> service.record(OutboxEvent.findById(eventId), channel));
    }

    private OutboxEvent event(String channel) {
        OutboxEvent event = OutboxEvent.create("Address", channel, "PET_STATE_CHANGED",
                "{\"address\":\"" + channel + "\",\"eventType\":\"PET_STATE_CHANGED\"}", Instant.now(), null);
        event.persistAndFlush();
        return event;
    }
    private String channel() { return "bc1q" + UUID.randomUUID().toString().replace("-", ""); }
}
