package br.com.satoshipet.api.art;

import br.com.satoshipet.api.pet.Pet;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Monta contexto congelado e prompt privado (ART-02). */
@ApplicationScoped
public class ArtworkContextBuilder {

    public FrozenGenerationContext buildContext(Pet pet, Instant now) {
        String timezone = pet.creatorAccount.timezone;
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime local = now.atZone(zone);
        String period = dayPeriod(local.toLocalTime());
        return new FrozenGenerationContext(
                pet.address.canonical,
                timezone,
                "desconhecida",
                "indisponivel",
                period,
                now.toString()
        );
    }

    public String buildPrompt(FrozenGenerationContext context) {
        return """
                Criatura original em pixel art 2D para endereço %s.
                Fuso: %s. Local: %s. Clima: %s. Período: %s.
                Fauna e flora regionais leves; sem marcas, retratos de pessoas, conteúdo adulto ou violência explícita.
                Mesma identidade visual em todas as poses.
                """.formatted(
                context.addressCanonical(),
                context.timezone(),
                context.locationLabel(),
                context.weatherSummary(),
                context.dayPeriod()
        ).trim();
    }

    private static String dayPeriod(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 5 && hour < 12) {
            return "day";
        }
        if (hour >= 12 && hour < 18) {
            return "afternoon";
        }
        if (hour >= 18 && hour < 22) {
            return "evening";
        }
        return "night";
    }
}
