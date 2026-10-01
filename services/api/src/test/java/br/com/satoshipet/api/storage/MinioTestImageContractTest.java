package br.com.satoshipet.api.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confere a proveniência da imagem real de teste, mesmo quando Docker está indisponível. */
class MinioTestImageContractTest {

    private static final String COMMIT = "07c3a429bfed433e49018cb0f78a52145d4bedeb";

    @Test
    void fixaCompiladorFonteEVerificacaoDeDependencias() throws IOException {
        String recipe = recipe();
        assertTrue(recipe.contains("FROM golang:1.24.6-bookworm@sha256:"
                + "ab1d1823abb55a9504d2e3e003b75b36dbeb1cbcc4c92593d85a84ee46becc6c AS build"));
        assertTrue(recipe.contains("git remote add origin https://github.com/minio/minio.git"));
        assertTrue(recipe.contains("git fetch --depth=1 origin " + COMMIT));
        assertTrue(recipe.contains("test \"$(git rev-parse HEAD)\" = \"" + COMMIT + "\""));
        assertTrue(recipe.contains("GOTOOLCHAIN=local"));
        assertTrue(recipe.contains("CGO_ENABLED=0"));
        assertTrue(recipe.contains("go mod verify"));
        assertTrue(recipe.contains("git diff --exit-code -- go.mod go.sum"));
        assertTrue(recipe.contains("go build -mod=readonly -trimpath"));
        assertTrue(recipe.contains("MINIO_RELEASE=RELEASE go run buildscripts/gen-ldflags.go"));
        assertTrue(recipe.contains("RUN ldflags=\"$(MINIO_RELEASE=RELEASE go run buildscripts/gen-ldflags.go)\""));
        assertTrue(recipe.contains("-ldflags=\"$ldflags\""));
    }

    @Test
    void runtimeNaoDependeDeImagemMinioRemotaEPreservaLicenca() throws IOException {
        String recipe = recipe();
        assertTrue(recipe.contains("FROM scratch"));
        assertTrue(recipe.contains("COPY --from=build /out/minio /minio"));
        assertTrue(recipe.contains("COPY --from=build /src/LICENSE /licenses/LICENSE"));
        assertTrue(recipe.contains("COPY --from=build /src/CREDITS /licenses/CREDITS"));
        assertTrue(recipe.contains("org.opencontainers.image.revision=\"" + COMMIT + "\""));
        assertTrue(recipe.contains("org.opencontainers.image.version=\"RELEASE.2025-09-07T16-13-09Z\""));
        assertTrue(recipe.contains("ENTRYPOINT [\"/minio\"]"));
        assertFalse(recipe.contains("FROM minio/minio"));
        assertFalse(recipe.contains("quay.io/minio"));
        assertFalse(recipe.contains("dl.min.io"));
        assertFalse(recipe.contains("@latest"));
    }

    private String recipe() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("minio/Dockerfile")) {
            assertNotNull(stream, "A receita da imagem de teste deve estar versionada no classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
