package br.com.satoshipet.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {

    @Test
    void habilitaJsonEmUmaLinhaComCamposOperacionais() throws IOException {
        Properties properties = loadConfiguration();

        assertEquals("${QUARKUS_LOG_CONSOLE_JSON_ENABLED:true}",
                properties.getProperty("quarkus.log.console.json.enabled"));
        assertEquals("false", properties.getProperty("quarkus.log.console.json.pretty-print"));
        assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                properties.getProperty("quarkus.log.console.json.date-format"));
        assertEquals("UTC", properties.getProperty("quarkus.log.console.json.zone-id"));
        assertEquals("false", properties.getProperty("quarkus.log.console.json.print-details"));
    }

    @Test
    void naoConfiguraArquivosNemCamposDeDadosPrivados() throws IOException {
        Properties properties = loadConfiguration();

        assertTrue(properties.stringPropertyNames().stream()
                .noneMatch(name -> name.startsWith("quarkus.log.file")));
        assertTrue(properties.stringPropertyNames().stream()
                .noneMatch(name -> name.contains("prompt")
                        || name.contains("token")
                        || name.contains("secret")
                        || name.contains("cookie")));
    }

    private Properties loadConfiguration() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/application.properties"))) {
            properties.load(reader);
        }
        return properties;
    }
}
