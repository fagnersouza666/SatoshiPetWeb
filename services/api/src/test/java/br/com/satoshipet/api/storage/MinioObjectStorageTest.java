package br.com.satoshipet.api.storage;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes de integração do adaptador com um servidor MinIO real. */
@Testcontainers(disabledWithoutDocker = true)
class MinioObjectStorageTest {

    private static final String MINIO_IMAGE = "quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z";
    private static final int S3_PORT = 9000;
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String APPROVED_BUCKET = "pet-artwork";
    private static final String STAGING_BUCKET = "pet-artwork-staging";

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(S3_PORT)
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/live")
                    .forPort(S3_PORT)
                    .forStatusCode(200));

    @Test
    void fazUploadELeituraNoBucketDeArtworkAprovado() throws IOException {
        ObjectStoragePort storage = storageFor(APPROVED_BUCKET);
        byte[] expected = "sprite aprovado".getBytes(StandardCharsets.UTF_8);

        storage.put(StorageNamespace.APPROVED, "test/aprovado/sprite.png", expected, "image/png");

        assertTrue(storage.exists(StorageNamespace.APPROVED, "test/aprovado/sprite.png"));
        try (InputStream result = storage.get(StorageNamespace.APPROVED, "test/aprovado/sprite.png")) {
            assertArrayEquals(expected, result.readAllBytes());
        }
    }

    @Test
    void mantemArtworkAprovadoEStagingIsoladosMesmoComAMesmaChave() throws IOException {
        String key = "test/isolation/sprite.png";
        byte[] approved = "conteudo aprovado".getBytes(StandardCharsets.UTF_8);
        byte[] staging = "preview staging".getBytes(StandardCharsets.UTF_8);
        MinioObjectStorage storage = storageFor(APPROVED_BUCKET);

        storage.put(StorageNamespace.APPROVED, key, approved, "image/png");
        storage.put(StorageNamespace.STAGING, key, staging, "image/png");

        try (InputStream result = storage.get(StorageNamespace.APPROVED, key)) {
            assertArrayEquals(approved, result.readAllBytes());
        }
        try (InputStream result = storage.get(StorageNamespace.STAGING, key)) {
            assertArrayEquals(staging, result.readAllBytes());
        }

        storage.delete(StorageNamespace.APPROVED, key);

        assertFalse(storage.exists(StorageNamespace.APPROVED, key));
        assertTrue(storage.exists(StorageNamespace.STAGING, key));
        try (InputStream result = storage.get(StorageNamespace.STAGING, key)) {
            assertArrayEquals(staging, result.readAllBytes());
        }
    }

    private static MinioObjectStorage storageFor(String bucket) {
        MinioObjectStorage storage = new MinioObjectStorage();
        storage.endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(S3_PORT);
        storage.accessKey = ACCESS_KEY;
        storage.secretKey = SECRET_KEY;
        storage.bucket = bucket;
        storage.stagingBucket = STAGING_BUCKET;
        storage.init();
        return storage;
    }
}
