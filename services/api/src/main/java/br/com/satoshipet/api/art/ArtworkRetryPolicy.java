package br.com.satoshipet.api.art;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Backoff para falhas técnicas de geração (ART-08). */
public final class ArtworkRetryPolicy {

    public static final int ALERT_THRESHOLD = 5;

    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30)
    );

    private ArtworkRetryPolicy() {
    }

    public static Instant nextRetryAt(int technicalAttemptCount, Instant now) {
        int index = Math.min(Math.max(technicalAttemptCount - 1, 0), BACKOFF.size() - 1);
        return now.plus(BACKOFF.get(index));
    }
}
