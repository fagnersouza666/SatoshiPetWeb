package br.com.satoshipet.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TlsProfileConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"staging", "prod"})
    void configuresProxyTerminatedHttps(String profile) throws IOException {
        Properties properties = loadProfile(profile);

        assertEquals("enabled", properties.getProperty("quarkus.http.insecure-requests"));
        assertEquals("true", properties.getProperty("quarkus.http.proxy.proxy-address-forwarding"));
        assertEquals("false", properties.getProperty("quarkus.http.proxy.allow-forwarded"));
        assertEquals("true", properties.getProperty("quarkus.http.proxy.allow-x-forwarded"));
        assertEquals("x-forwarded", properties.getProperty("quarkus.http.proxy.forwarded-precedence"));
        assertEquals("true", properties.getProperty("quarkus.http.proxy.enable-forwarded-host"));
        assertEquals("true", properties.getProperty("quarkus.http.proxy.strict-forwarded-control"));
        assertEquals("${QUARKUS_HTTP_PROXY_TRUSTED_PROXIES}",
                properties.getProperty("quarkus.http.proxy.trusted-proxies"));
    }

    private Properties loadProfile(String profile) throws IOException {
        String resourceName = "application-" + profile + ".properties";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IOException("Recurso ausente: " + resourceName);
            }
            Properties properties = new Properties();
            properties.load(stream);
            return properties;
        }
    }
}
