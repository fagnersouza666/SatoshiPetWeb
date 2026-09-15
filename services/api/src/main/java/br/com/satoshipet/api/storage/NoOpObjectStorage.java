package br.com.satoshipet.api.storage;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória do {@link ObjectStoragePort}.
 * Usada como bean padrão quando o MinIO não estiver configurado (testes e
 * execuções locais sem Docker).
 */
@ApplicationScoped
@DefaultBean
public class NoOpObjectStorage implements ObjectStoragePort {

    private static final Logger LOG = Logger.getLogger(NoOpObjectStorage.class);

    private final ConcurrentHashMap<StorageNamespace, ConcurrentHashMap<String, byte[]>> stores =
            new ConcurrentHashMap<>();

    public NoOpObjectStorage() {
        stores.put(StorageNamespace.APPROVED, new ConcurrentHashMap<>());
        stores.put(StorageNamespace.STAGING, new ConcurrentHashMap<>());
    }

    private ConcurrentHashMap<String, byte[]> storeFor(StorageNamespace namespace) {
        return stores.computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>());
    }

    @Override
    public void put(StorageNamespace namespace, String key, byte[] data, String contentType) {
        storeFor(namespace).put(key, data.clone());
        LOG.debugf("[NoOp Storage] PUT ns=%s key=%s size=%d contentType=%s",
                namespace, key, data.length, contentType);
    }

    @Override
    public InputStream get(StorageNamespace namespace, String key) {
        byte[] data = storeFor(namespace).get(key);
        if (data == null) {
            throw new ObjectNotFoundException(key);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public void delete(StorageNamespace namespace, String key) {
        storeFor(namespace).remove(key);
        LOG.debugf("[NoOp Storage] DELETE ns=%s key=%s", namespace, key);
    }

    @Override
    public boolean exists(StorageNamespace namespace, String key) {
        return storeFor(namespace).containsKey(key);
    }

    @Override
    public void promote(List<String> keys) {
        promote(keys, null, null);
    }

    @Override
    public void promote(List<String> keys, String stagingPrefix, String approvedPrefix) {
        Objects.requireNonNull(keys, "keys");
        ConcurrentHashMap<String, byte[]> staging = storeFor(StorageNamespace.STAGING);
        ConcurrentHashMap<String, byte[]> approved = storeFor(StorageNamespace.APPROVED);
        for (String key : keys) {
            byte[] data = staging.get(key);
            if (data != null) {
                approved.put(remapKey(key, stagingPrefix, approvedPrefix), data.clone());
            }
        }
    }

    private static String remapKey(String key, String stagingPrefix, String approvedPrefix) {
        if (stagingPrefix == null || approvedPrefix == null || !key.startsWith(stagingPrefix)) {
            return key;
        }
        return approvedPrefix + key.substring(stagingPrefix.length());
    }
}
