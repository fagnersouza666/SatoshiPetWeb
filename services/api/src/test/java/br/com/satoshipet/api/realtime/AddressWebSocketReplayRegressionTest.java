package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.pet.PetPublicSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AddressWebSocketReplayRegressionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private AddressWebSocket endpoint;
    private WebSocketConnection connection;
    private List<String> sent;

    @BeforeEach
    void setup() {
        endpoint = new AddressWebSocket();
        endpoint.objectMapper = mapper;
        endpoint.cursorService = mock(RealtimeEventCursorService.class);
        when(endpoint.cursorService.currentCursor("bc1qreplay")).thenReturn("0");
        connection = mock(WebSocketConnection.class);
        when(connection.id()).thenReturn("replay-regression");
        when(connection.pathParam("canonical")).thenReturn("bc1qreplay");
        when(connection.isOpen()).thenReturn(true);
        when(connection.userData()).thenReturn(new TestUserData());
        when(connection.handshakeRequest()).thenReturn(mock(HandshakeRequest.class));
        sent = new ArrayList<>();
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(connection).sendTextAndAwait(anyString());
        try (var pet = mockStatic(PetPublicSnapshot.class)) {
            endpoint.onOpen(connection);
        }
        sent.clear();
    }

    @Test
    void replayEntregaPrimeiroEventoAntesDosDemaisSemFrameExtra() throws Exception {
        when(endpoint.cursorService.replayAfter("bc1qreplay", "0")).thenReturn(new RealtimeEventCursorService.Replay("3", List.of(
                new RealtimeEventCursorService.StoredEvent("1", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED")),
                new RealtimeEventCursorService.StoredEvent("2", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED")),
                new RealtimeEventCursorService.StoredEvent("3", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED"))), false));
        String additional = endpoint.onMessage(connection, "{\"type\":\"RECONNECT\",\"cursor\":\"0\"}");
        assertNull(additional, "Todos os frames devem seguir pelo mesmo caminho de envio");
        assertEquals(List.of("1", "2", "3"), cursors());
    }

    @Test
    void falhaDeEnvioInterrompeReplaySemPularEvento() throws Exception {
        when(endpoint.cursorService.replayAfter("bc1qreplay", "0")).thenReturn(new RealtimeEventCursorService.Replay("3", List.of(
                new RealtimeEventCursorService.StoredEvent("1", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED")),
                new RealtimeEventCursorService.StoredEvent("2", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED")),
                new RealtimeEventCursorService.StoredEvent("3", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED"))), false));
        doAnswer(invocation -> {
            String payload = invocation.getArgument(0);
            sent.add(payload);
            if (mapper.readTree(payload).path("cursor").asText().equals("2")) throw new IllegalStateException("envio falhou");
            return null;
        }).when(connection).sendTextAndAwait(anyString());
        assertNull(endpoint.onMessage(connection, "{\"type\":\"RECONNECT\",\"cursor\":\"0\"}"));
        assertEquals(List.of("1", "2"), cursors());
    }

    @Test
    void cursorAntigoDeOutroProcessoNaoViraCursorDoServidor() throws Exception {
        when(endpoint.cursorService.replayAfter("bc1qreplay", "9000"))
                .thenReturn(new RealtimeEventCursorService.Replay("0", List.of(), true));
        try (var pet = mockStatic(PetPublicSnapshot.class)) {
            String additional = endpoint.onMessage(connection, "{\"type\":\"RECONNECT\",\"cursor\":\"9000\"}");
            if (additional != null) sent.add(additional);
        }
        assertEquals("SNAPSHOT", mapper.readTree(sent.getFirst()).path("type").asText());
        assertEquals("0", mapper.readTree(sent.getFirst()).path("cursor").asText());
    }

    @Test
    void fanoutRetomaDoUltimoEnvioConfirmadoENaoReenviaNoPollSeguinte() throws Exception {
        endpoint.openConnections = mock(OpenConnections.class);
        when(endpoint.openConnections.stream()).thenAnswer(ignored -> java.util.stream.Stream.of(connection));
        var event = new RealtimeEventCursorService.StoredEvent("5", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED"));
        when(endpoint.cursorService.replayAfter("bc1qreplay", "0"))
                .thenReturn(new RealtimeEventCursorService.Replay("5", List.of(event), false));
        when(endpoint.cursorService.replayAfter("bc1qreplay", "5"))
                .thenReturn(new RealtimeEventCursorService.Replay("5", List.of(), false));
        AddressWebSocketFanout fanout = new AddressWebSocketFanout();
        fanout.addressWebSocket = endpoint;
        fanout.poll();
        fanout.poll();
        assertEquals(List.of("5"), cursors());
        endpoint.onClose(connection);
        clearInvocations(endpoint.cursorService);
        endpoint.pollCommittedEvents();
        verifyNoInteractions(endpoint.cursorService);
    }

    @Test
    void conexoesEmDuasReplicasRecebemMesmaProjecaoDuravel() throws Exception {
        endpoint.openConnections = mock(OpenConnections.class);
        when(endpoint.openConnections.stream()).thenAnswer(ignored -> java.util.stream.Stream.of(connection));
        var event = new RealtimeEventCursorService.StoredEvent("7", "PET_STATE_CHANGED", Map.of("eventType", "PET_STATE_CHANGED"));
        when(endpoint.cursorService.replayAfter("bc1qreplay", "0"))
                .thenReturn(new RealtimeEventCursorService.Replay("7", List.of(event), false));
        AddressWebSocket replica = new AddressWebSocket();
        replica.cursorService = endpoint.cursorService;
        replica.objectMapper = mapper;
        replica.openConnections = mock(OpenConnections.class);
        WebSocketConnection remote = mock(WebSocketConnection.class);
        when(remote.id()).thenReturn("remote-connection");
        when(remote.isOpen()).thenReturn(true);
        when(remote.pathParam("canonical")).thenReturn("bc1qreplay");
        when(remote.userData()).thenReturn(new TestUserData());
        when(remote.handshakeRequest()).thenReturn(mock(HandshakeRequest.class));
        when(replica.openConnections.stream()).thenAnswer(ignored -> java.util.stream.Stream.of(remote));
        List<String> remoteFrames = new ArrayList<>();
        doAnswer(invocation -> { remoteFrames.add(invocation.getArgument(0)); return null; })
                .when(remote).sendTextAndAwait(anyString());
        try (var pet = mockStatic(PetPublicSnapshot.class)) { replica.onOpen(remote); }
        remoteFrames.clear();
        endpoint.pollCommittedEvents();
        replica.pollCommittedEvents();
        assertEquals(List.of("7"), cursors());
        assertEquals("7", mapper.readTree(remoteFrames.getFirst()).path("cursor").asText());
    }

    @Test
    void pollComJanelaExpiradaEntregaSnapshotEContinuaNoCursorAtual() throws Exception {
        endpoint.openConnections = mock(OpenConnections.class);
        when(endpoint.openConnections.stream()).thenAnswer(ignored -> java.util.stream.Stream.of(connection));
        when(endpoint.cursorService.replayAfter("bc1qreplay", "0"))
                .thenReturn(new RealtimeEventCursorService.Replay("301", List.of(), true));
        when(endpoint.cursorService.replayAfter("bc1qreplay", "301"))
                .thenReturn(new RealtimeEventCursorService.Replay("301", List.of(), false));
        try (var pet = mockStatic(PetPublicSnapshot.class)) { endpoint.pollCommittedEvents(); }
        endpoint.pollCommittedEvents();
        assertEquals(List.of("301"), cursors());
        assertEquals("SNAPSHOT", mapper.readTree(sent.getFirst()).path("type").asText());
    }

    private List<String> cursors() throws Exception {
        List<String> result = new ArrayList<>();
        for (String raw : sent) result.add(mapper.readTree(raw).path("cursor").asText());
        return result;
    }

    static class TestUserData implements UserData {
        final Map<TypedKey<?>, Object> values = new HashMap<>();
        @SuppressWarnings("unchecked")
        public <T> T get(TypedKey<T> key) { return (T) values.get(key); }
        @SuppressWarnings("unchecked")
        public <T> T put(TypedKey<T> key, T value) { return (T) values.put(key, value); }
        @SuppressWarnings("unchecked")
        public <T> T remove(TypedKey<T> key) { return (T) values.remove(key); }
        public int size() { return values.size(); }
        public void clear() { values.clear(); }
    }
}
