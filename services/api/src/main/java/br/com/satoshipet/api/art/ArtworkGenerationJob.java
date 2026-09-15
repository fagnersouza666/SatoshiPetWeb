package br.com.satoshipet.api.art;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Job de geração com trava distribuída (ART-08). */
@ApplicationScoped
public class ArtworkGenerationJob {

    private static final Logger LOG = Logger.getLogger(ArtworkGenerationJob.class);

    static final String JOB_NAME = "artwork-generate";
    static final Duration LOCK_TTL = Duration.ofSeconds(120);

    private final JobLockService jobLockService;
    private final ArtworkPipeline pipeline;
    private final String ownerId = UUID.randomUUID().toString();

    @Inject
    public ArtworkGenerationJob(JobLockService jobLockService, ArtworkPipeline pipeline) {
        this.jobLockService = jobLockService;
        this.pipeline = pipeline;
    }

    @Scheduled(every = "30s", identity = JOB_NAME)
    public void tick() {
        if (!jobLockService.acquire(JOB_NAME, ownerId, LOCK_TTL)) {
            return;
        }
        try {
            pipeline.processReadyWorkloads(Instant.now());
        } catch (Exception e) {
            LOG.errorf(e, "Falha no ciclo de geração de arte");
        } finally {
            jobLockService.release(JOB_NAME, ownerId);
        }
    }
}
