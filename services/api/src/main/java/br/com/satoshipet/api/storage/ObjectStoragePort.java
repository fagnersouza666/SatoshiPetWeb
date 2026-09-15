package br.com.satoshipet.api.storage;

import java.io.InputStream;
import java.util.List;

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
     * Armazena um objeto identificado pela chave no namespace informado.
     *
     * @param namespace   bucket aprovado ou staging
     * @param key         chave única dentro do bucket (ex: "pets/uuid/v1/atlas.png")
     * @param data        conteúdo do objeto
     * @param contentType tipo MIME (ex: "image/png")
     */
    void put(StorageNamespace namespace, String key, byte[] data, String contentType);

    /**
     * Recupera o conteúdo de um objeto pelo chave.
     *
     * @param namespace bucket aprovado ou staging
     * @param key       chave do objeto
     * @return stream de leitura; o chamador é responsável por fechar
     * @throws ObjectNotFoundException se a chave não existir
     */
    InputStream get(StorageNamespace namespace, String key);

    /**
     * Remove um objeto. Idempotente: não lança erro se a chave não existir.
     *
     * @param namespace bucket aprovado ou staging
     * @param key       chave do objeto a remover
     */
    void delete(StorageNamespace namespace, String key);

    /**
     * Verifica se a chave existe no storage.
     *
     * @param namespace bucket aprovado ou staging
     * @param key       chave a verificar
     * @return {@code true} se existir
     */
    boolean exists(StorageNamespace namespace, String key);

    /**
     * Copia objetos do staging para o bucket aprovado (promote na aprovação).
     *
     * @param keys chaves relativas a copiar
     */
    void promote(List<String> keys);

    /**
     * Copia objetos do staging para o bucket aprovado, remapeando o prefixo quando informado.
     *
     * @param keys            chaves no staging
     * @param stagingPrefix   prefixo de origem (ex.: {@code pets/{id}/v1/attempt-1})
     * @param approvedPrefix  prefixo de destino (ex.: {@code pets/{id}/v1})
     */
    default void promote(List<String> keys, String stagingPrefix, String approvedPrefix) {
        promote(keys);
    }

    /** Compatibilidade: grava no bucket aprovado. */
    default void put(String key, byte[] data, String contentType) {
        put(StorageNamespace.APPROVED, key, data, contentType);
    }

    /** Compatibilidade: lê do bucket aprovado. */
    default InputStream get(String key) {
        return get(StorageNamespace.APPROVED, key);
    }

    /** Compatibilidade: remove do bucket aprovado. */
    default void delete(String key) {
        delete(StorageNamespace.APPROVED, key);
    }

    /** Compatibilidade: verifica existência no bucket aprovado. */
    default boolean exists(String key) {
        return exists(StorageNamespace.APPROVED, key);
    }
}
