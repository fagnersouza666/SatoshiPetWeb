package br.com.satoshipet.api.account;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/** Configuração de sessão autenticada. */
@ConfigMapping(prefix = "satoshi-pet.session")
public interface SessionConfiguration {

    /** Duração padrão da sessão após login. */
    @WithDefault("P30D")
    Duration duration();
}
