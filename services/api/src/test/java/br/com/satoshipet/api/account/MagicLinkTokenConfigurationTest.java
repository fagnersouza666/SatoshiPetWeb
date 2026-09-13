package br.com.satoshipet.api.account;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicLinkTokenConfigurationTest {

    @Test
    void acceptsConfiguredPositiveTtl() {
        MagicLinkTokenConfiguration configuration = stub(Optional.of(Duration.ofMinutes(5)));

        assertEquals(Duration.ofMinutes(5), configuration.requiredTtl());
    }

    @Test
    void rejectsMissingTtlBecauseNoProductDefaultWasChosen() {
        MagicLinkTokenConfiguration configuration = stub(Optional.empty());

        assertThrows(IllegalStateException.class, configuration::requiredTtl);
    }

    @Test
    void rejectsNonPositiveTtl() {
        MagicLinkTokenConfiguration configuration = stub(Optional.of(Duration.ZERO));

        assertThrows(IllegalStateException.class, configuration::requiredTtl);
    }

    private static MagicLinkTokenConfiguration stub(Optional<Duration> ttl) {
        return new MagicLinkTokenConfiguration() {
            @Override
            public String baseUrl() {
                return "http://localhost:4200";
            }

            @Override
            public Optional<Duration> ttl() {
                return ttl;
            }
        };
    }
}
