package br.com.satoshipet.api.storage;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória do {@link ObjectStoragePort}.
 * Usada como bean padrão quando o MinIO não estiver configurado (testes e
 * execuções locais sem Docker).
 *
 * <p>NÃO persiste dados entre reinicializações; adequada apenas para testes
 * e verificações de contrato.</p>
 */
@ApplicationScoped
@DefaultBean
public class NoOpObjectStorage implements ObjectStoragePort {

    private static final Logger LOG = Logger.getLogger(NoOpObjectStorage.class);

    private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] data, String contentType) {
        store.put(key, data.clone());
        LOG.debugf("[NoOp Storage] PUT key=%s size=%d contentType=%s", key, data.length, contentType);
    }

    @Override
    public InputStream get(String key) {
        byte[] data = store.get(key);
        if (data == null) {
            throw new ObjectNotFoundException(key);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
        LOG.debugf("[NoOp Storage] DELETE key=%s", key);
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }
}
