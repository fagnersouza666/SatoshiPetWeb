package br.com.satoshipet.api.account;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Optional;

/** Configuração operacional do magic link, sem escolher um TTL de produto. */
@ConfigMapping(prefix = "satoshi-pet.magic-link")
public interface MagicLinkTokenConfiguration {

    /** URL base da PWA para montar o link enviado por e-mail. */
    @WithName("base-url")
    @WithDefault("http://localhost:4200")
    String baseUrl();

    /**
     * Duração do link a partir da emissão. A ausência é válida no scaffold, mas
     * impede a emissão até que o ambiente forneça o parâmetro decidido.
     */
    Optional<Duration> ttl();

    default Duration requiredTtl() {
        Duration configured = ttl().orElseThrow(() ->
                new IllegalStateException("satoshi-pet.magic-link.ttl não configurado"));

        if (configured.isZero() || configured.isNegative()) {
            throw new IllegalStateException("satoshi-pet.magic-link.ttl deve ser positivo");
        }
        return configured;
    }
}
