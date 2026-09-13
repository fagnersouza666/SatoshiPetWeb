package br.com.satoshipet.api.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Adaptador de object storage usando MinIO (S3-compatível).
 * Ativo apenas nos perfis {@code dev} e {@code prod}.
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "prod"})
public class MinioObjectStorage implements ObjectStoragePort {

    private static final Logger LOG = Logger.getLogger(MinioObjectStorage.class);

    @ConfigProperty(name = "satoshi-pet.storage.endpoint")
    String endpoint;

    @ConfigProperty(name = "satoshi-pet.storage.access-key")
    String accessKey;

    @ConfigProperty(name = "satoshi-pet.storage.secret-key")
    String secretKey;

    @ConfigProperty(name = "satoshi-pet.storage.bucket", defaultValue = "pet-artwork")
    String bucket;

    @ConfigProperty(name = "satoshi-pet.storage.staging-bucket", defaultValue = "pet-artwork-staging")
    String stagingBucket;

    private MinioClient client;

    @PostConstruct
    void init() {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        ensureBucketExists(bucket);
        ensureBucketExists(stagingBucket);
    }

    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                LOG.infof("Bucket '%s' criado no MinIO.", bucketName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao verificar/criar bucket MinIO: " + bucketName, e);
        }
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Falha ao armazenar objeto: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new ObjectNotFoundException(key);
            }
            throw new StorageException("Falha ao recuperar objeto: " + key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao recuperar objeto: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            // Idempotente: ignora erros de chave inexistente.
            LOG.debugf("Objeto '%s' não encontrado ao deletar (ignorado).", key);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageException("Falha ao verificar existência: " + key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao verificar existência: " + key, e);
        }
    }

    /** Exceção de infraestrutura do storage (não exposta ao domínio). */
    static class StorageException extends RuntimeException {
        StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
