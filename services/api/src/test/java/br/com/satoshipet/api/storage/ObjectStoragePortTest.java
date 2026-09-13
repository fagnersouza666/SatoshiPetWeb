package br.com.satoshipet.api.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de contrato do {@link ObjectStoragePort} usando a implementação
 * em memória {@link NoOpObjectStorage}.
 *
 * <p>Não requer Quarkus, Docker ou MinIO — execução puramente in-process.</p>
 */
class ObjectStoragePortTest {

    private ObjectStoragePort storage;

    @BeforeEach
    void setUp() {
        // Usa a implementação em memória para testes de contrato
        storage = new NoOpObjectStorage();
    }

    @Test
    void armazenaERecuperaConteudoCorretamente() throws IOException {
        byte[] data = "conteúdo de teste".getBytes(StandardCharsets.UTF_8);
        String key = "test/imagem.png";

        storage.put(key, data, "image/png");

        try (InputStream result = storage.get(key)) {
            byte[] retrieved = result.readAllBytes();
            assertArrayEquals(data, retrieved, "Conteúdo recuperado deve ser idêntico ao armazenado");
        }
    }

    @Test
    void existsRetornaTrueAposInserir() {
        String key = "test/exists-check.json";
        storage.put(key, "{}".getBytes(StandardCharsets.UTF_8), "application/json");

        assertTrue(storage.exists(key));
    }

    @Test
    void existsRetornaFalseParaChaveInexistente() {
        assertFalse(storage.exists("inexistente/chave.png"));
    }

    @Test
    void deleteRemoveObjeto() {
        String key = "test/para-deletar.txt";
        storage.put(key, "dados".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertTrue(storage.exists(key));

        storage.delete(key);

        assertFalse(storage.exists(key));
    }

    @Test
    void deleteIdempotente_naoLancaErroParaChaveInexistente() {
        // Não deve lançar exceção
        storage.delete("chave/inexistente.png");
        storage.delete("chave/inexistente.png"); // segunda chamada também deve ser silenciosa
    }

    @Test
    void getLancaObjectNotFoundParaChaveInexistente() {
        assertThrows(ObjectNotFoundException.class, () -> storage.get("nao-existe.png"));
    }

    @Test
    void putSubstituiConteudoExistente() throws IOException {
        String key = "test/substitui.txt";
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] updated  = "atualizado".getBytes(StandardCharsets.UTF_8);

        storage.put(key, original, "text/plain");
        storage.put(key, updated, "text/plain");

        try (InputStream result = storage.get(key)) {
            assertArrayEquals(updated, result.readAllBytes(),
                    "Deve retornar conteúdo mais recente");
        }
    }

    @Test
    void armazenaMuitasChavesIndependentes() {
        for (int i = 0; i < 10; i++) {
            String key = "test/multi-" + i + ".bin";
            byte[] data = ("dado-" + i).getBytes(StandardCharsets.UTF_8);
            storage.put(key, data, "application/octet-stream");
        }

        for (int i = 0; i < 10; i++) {
            assertTrue(storage.exists("test/multi-" + i + ".bin"));
        }
    }
}
