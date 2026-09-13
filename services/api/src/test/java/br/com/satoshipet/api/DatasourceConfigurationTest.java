package br.com.satoshipet.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatasourceConfigurationTest {

    @Test
    void usaPostgresEPoliticaDeSchemaNosPerfisExecutaveis() throws IOException {
        Properties properties = loadMainConfiguration();

        assertEquals("postgresql", properties.getProperty("quarkus.datasource.db-kind"));
        assertEquals("true", properties.getProperty("quarkus.datasource.health.enabled"));
        assertEquals("validate",
                properties.getProperty("quarkus.hibernate-orm.schema-management.strategy"));

        assertEquals(
                "${QUARKUS_DATASOURCE_JDBC_URL:jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:satoshi_pet}}",
                properties.getProperty("%dev.quarkus.datasource.jdbc.url"));
        assertEquals("${QUARKUS_DATASOURCE_USERNAME:${POSTGRES_USER}}",
                properties.getProperty("%dev.quarkus.datasource.username"));
        assertEquals("${QUARKUS_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD:}}",
                properties.getProperty("%dev.quarkus.datasource.password"));

        assertEquals(
                "${QUARKUS_DATASOURCE_JDBC_URL:jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}}",
                properties.getProperty("%prod.quarkus.datasource.jdbc.url"));
        assertEquals("${QUARKUS_DATASOURCE_USERNAME:${POSTGRES_USER}}",
                properties.getProperty("%prod.quarkus.datasource.username"));
        assertEquals("${QUARKUS_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD:}}",
                properties.getProperty("%prod.quarkus.datasource.password"));

        assertFalse(properties.getProperty("%prod.quarkus.datasource.username").contains(":satoshi"));
        assertFalse(properties.getProperty("%prod.quarkus.datasource.password").contains(":satoshi"));
    }

    @Test
    void mantémH2IsoladoNoPerfilDeTeste() throws IOException {
        Properties properties = loadTestConfiguration();

        assertEquals("h2", properties.getProperty("quarkus.datasource.db-kind"));
        assertEquals("jdbc:h2:mem:satoshi_pet_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                properties.getProperty("quarkus.datasource.jdbc.url"));
        assertEquals("none",
                properties.getProperty("quarkus.hibernate-orm.schema-management.strategy"));
    }

    private Properties loadMainConfiguration() throws IOException {
        return loadConfiguration(Path.of("src/main/resources/application.properties"));
    }

    private Properties loadTestConfiguration() throws IOException {
        return loadConfiguration(Path.of("src/test/resources/application.properties"));
    }

    private Properties loadConfiguration(Path path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }
}
