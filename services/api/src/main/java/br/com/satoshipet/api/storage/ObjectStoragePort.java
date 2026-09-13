package br.com.satoshipet.api.storage;

import java.io.InputStream;

/**
 * Porta de saída para armazenamento de objetos (S3-compatível).
 *
 * <p>Adapters disponíveis:
 * <ul>
 *   <li>{@link MinioObjectStorage} — MinIO em dev/prod</li>
 *   <li>{@link NoOpObjectStorage}  — stub sem estado para testes</li>
 * </ul>
 * </p>
 */
public interface ObjectStoragePort {

    /**
     * Armazena um objeto identificado pela chave.
     *
     * @param key         chave única dentro do bucket (ex: "pets/uuid.png")
     * @param data        conteúdo do objeto
     * @param contentType tipo MIME (ex: "image/png")
     */
    void put(String key, byte[] data, String contentType);

    /**
     * Recupera o conteúdo de um objeto pelo chave.
     *
     * @param key chave do objeto
     * @return stream de leitura; o chamador é responsável por fechar
     * @throws ObjectNotFoundException se a chave não existir
     */
    InputStream get(String key);

    /**
     * Remove um objeto. Idempotente: não lança erro se a chave não existir.
     *
     * @param key chave do objeto a remover
     */
    void delete(String key);

    /**
     * Verifica se a chave existe no storage.
     *
     * @param key chave a verificar
     * @return {@code true} se existir
     */
    boolean exists(String key);
}
