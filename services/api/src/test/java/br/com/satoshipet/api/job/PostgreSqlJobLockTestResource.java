package br.com.satoshipet.api.job;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/** Fornece um PostgreSQL isolado para o cenário de disputa entre réplicas. */
public final class PostgreSqlJobLockTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
                .withDatabaseName("satoshi_pet_job_lock_test")
                .withUsername("satoshi")
                .withPassword("satoshi-test-password");
        postgres.start();

        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword(),
                "quarkus.datasource.devservices.enabled", "false"
        );
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
