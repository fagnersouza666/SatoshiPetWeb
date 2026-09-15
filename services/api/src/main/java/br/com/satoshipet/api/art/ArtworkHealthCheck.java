package br.com.satoshipet.api.art;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.time.Instant;
import java.util.List;

/** Health check operacional da geração de arte (ART-08). */
@ApplicationScoped
@Readiness
public class ArtworkHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("artwork-generation");
        List<PetArtwork> alerting = PetArtwork.list(
                "technicalAttemptCount >= ?1 AND generationStatus = ?2",
                ArtworkRetryPolicy.ALERT_THRESHOLD,
                ArtGenerationStatus.RETRY_WAIT
        );
        if (alerting.isEmpty()) {
            return builder.up().build();
        }
        PetArtwork sample = alerting.getFirst();
        return builder.down()
                .withData("stuckArtworks", alerting.size())
                .withData("lastFailureCode", sample.lastFailureCode)
                .withData("lastFailureAt", sample.lastFailureAt == null
                        ? null
                        : sample.lastFailureAt.toString())
                .withData("checkedAt", Instant.now().toString())
                .build();
    }
}
