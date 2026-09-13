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

        storage.put("test/aprovado/sprite.png", expected, "image/png");

        assertTrue(storage.exists("test/aprovado/sprite.png"));
        try (InputStream result = storage.get("test/aprovado/sprite.png")) {
            assertArrayEquals(expected, result.readAllBytes());
        }
    }

    @Test
    void mantemArtworkAprovadoEStagingIsoladosMesmoComAMesmaChave() throws IOException {
        String key = "test/isolation/sprite.png";
        byte[] approved = "conteudo aprovado".getBytes(StandardCharsets.UTF_8);
        byte[] staging = "preview staging".getBytes(StandardCharsets.UTF_8);
        ObjectStoragePort approvedStorage = storageFor(APPROVED_BUCKET);
        ObjectStoragePort stagingStorage = storageFor(STAGING_BUCKET);

        approvedStorage.put(key, approved, "image/png");
        stagingStorage.put(key, staging, "image/png");

        try (InputStream result = approvedStorage.get(key)) {
            assertArrayEquals(approved, result.readAllBytes());
        }
        try (InputStream result = stagingStorage.get(key)) {
            assertArrayEquals(staging, result.readAllBytes());
        }

        approvedStorage.delete(key);

        assertFalse(approvedStorage.exists(key));
        assertTrue(stagingStorage.exists(key));
        try (InputStream result = stagingStorage.get(key)) {
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
