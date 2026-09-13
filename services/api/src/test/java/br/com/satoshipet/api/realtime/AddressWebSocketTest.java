package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AddressWebSocketTest {

    private static final String TEST_ADDRESS = BitcoinTestAddresses.REGTEST_BECH32;

    @TestHTTPResource("/")
    URI baseUri;

    @Test
    void recebeSnapshotAoConectar() throws Exception {
        URI webSocketUri = wsUriForAddress(TEST_ADDRESS);

        CompletableFuture<String> firstMessage = new CompletableFuture<>();

        HttpClient client = HttpClient.newHttpClient();
        WebSocket ws = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(webSocketUri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public java.util.concurrent.CompletionStage<?> onText(
                            WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            firstMessage.complete(buffer.toString());
                            buffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        firstMessage.completeExceptionally(error);
                    }
                })
                .get(5, TimeUnit.SECONDS);

        String snapshot;
        try {
            snapshot = firstMessage.get(5, TimeUnit.SECONDS);
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        }

        assertNotNull(snapshot, "Servidor deve enviar mensagem ao conectar");
        assertTrue(snapshot.contains("\"type\":\"SNAPSHOT\""),
                "Primeira mensagem deve ser SNAPSHOT, recebido: " + snapshot);
        assertTrue(snapshot.contains(TEST_ADDRESS),
                "Snapshot deve conter o endereço do canal: " + snapshot);
    }

    @Test
    void recebeSnapshotAoReconectarComCursor() throws Exception {
        URI webSocketUri = wsUriForAddress(TEST_ADDRESS);

        CompletableFuture<String> reconnectSnapshot = new CompletableFuture<>();

        HttpClient client = HttpClient.newHttpClient();
        WebSocket ws = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(webSocketUri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();
                    private int messageCount = 0;

                    @Override
                    public java.util.concurrent.CompletionStage<?> onText(
                            WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            messageCount++;
                            String msg = buffer.toString();
                            buffer.setLength(0);

                            if (messageCount == 1) {
                                webSocket.sendText("""
                                        {"type":"RECONNECT","cursor":"42"}
                                        """, true);
                            } else if (messageCount == 2) {
                                reconnectSnapshot.complete(msg);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        reconnectSnapshot.completeExceptionally(error);
                    }
                })
                .get(5, TimeUnit.SECONDS);

        String snapshot;
        try {
            snapshot = reconnectSnapshot.get(5, TimeUnit.SECONDS);
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        }

        assertNotNull(snapshot);
        assertTrue(snapshot.contains("\"type\":\"SNAPSHOT\""),
                "Reconexão deve retornar SNAPSHOT, recebido: " + snapshot);
        assertTrue(snapshot.contains("\"cursor\":\"42\""),
                "Snapshot deve refletir o cursor enviado: " + snapshot);
    }

    private URI wsUriForAddress(String address) throws Exception {
        String httpBase = baseUri.toString();
        if (!httpBase.endsWith("/")) {
            httpBase += "/";
        }
        return new URI(httpBase.replaceFirst("^http", "ws") + "api/ws/address/" + address);
    }
}
