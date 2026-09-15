package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.job.JobLockService;
import br.com.satoshipet.api.platform.CorrelationIdContext;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consome reserva e carência no relógio do servidor, mesmo com o app fechado
 * (PET-18, CA-025).
 *
 * <p>Habilitado em produção e desabilitado em testes
 * ({@code quarkus.scheduler.enabled=false} no perfil de testes).</p>
 */
@ApplicationScoped
public class PetTickJob {

    private static final Logger LOG = Logger.getLogger(PetTickJob.class);

    /** Nome do lock distribuído e identity do agendamento. */
    static final String JOB_NAME = "pet-tick";

    /** TTL do lock — maior que a duração esperada do ciclo. */
    static final Duration LOCK_TTL = Duration.ofSeconds(120);

    private final JobLockService jobLockService;
    private final PetLifecyclePort petLifecycle;
    private final String ownerId = UUID.randomUUID().toString();

    @Inject
    public PetTickJob(JobLockService jobLockService, PetLifecyclePort petLifecycle) {
        this.jobLockService = jobLockService;
        this.petLifecycle = petLifecycle;
    }

    @Scheduled(every = "60s", identity = JOB_NAME)
    public void tick() {
        if (!jobLockService.acquire(JOB_NAME, ownerId, LOCK_TTL)) {
            LOG.debugf("Lock do job '%s' detido por outra instância — ciclo ignorado", JOB_NAME);
            return;
        }
        try {
            try (CorrelationIdContext.Scope ignored = CorrelationIdContext.openNew()) {
                runTickCycle();
            }
        } finally {
            jobLockService.release(JOB_NAME, ownerId);
        }
    }

    /**
     * Tique UTC de todos os pets. Visibilidade de pacote para testes.
     */
    void runTickCycle() {
        Instant now = Instant.now();
        List<Pet> pets = Pet.listAll();
        if (pets.isEmpty()) {
            LOG.debugf("Nenhum pet para tique.");
            return;
        }
        LOG.infof("Iniciando ciclo de tique do pet para %d pet(s).", pets.size());
        int successCount = 0;
        int failCount = 0;
        for (Pet pet : pets) {
            try {
                petLifecycle.tick(pet.id, now);
                successCount++;
            } catch (Exception e) {
                failCount++;
                LOG.errorf(e, "Falha no tique do pet=%s — continua para o próximo", pet.id);
            }
        }
        LOG.infof("Ciclo de tique do pet concluído: %d sucesso(s), %d falha(s).", successCount, failCount);
    }
}
