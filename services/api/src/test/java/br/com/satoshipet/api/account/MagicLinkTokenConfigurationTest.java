package br.com.satoshipet.api.account;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicLinkTokenConfigurationTest {

    @Test
    void acceptsConfiguredPositiveTtl() {
        MagicLinkTokenConfiguration configuration = () -> Optional.of(Duration.ofMinutes(5));

        assertEquals(Duration.ofMinutes(5), configuration.requiredTtl());
    }

    @Test
    void rejectsMissingTtlBecauseNoProductDefaultWasChosen() {
        MagicLinkTokenConfiguration configuration = Optional::<Duration>empty;

        assertThrows(IllegalStateException.class, configuration::requiredTtl);
    }

    @Test
    void rejectsNonPositiveTtl() {
        MagicLinkTokenConfiguration configuration = () -> Optional.of(Duration.ZERO);

        assertThrows(IllegalStateException.class, configuration::requiredTtl);
    }
}
