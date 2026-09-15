package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.job.JobLockService;
import br.com.satoshipet.api.platform.CorrelationIdContext;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Job agendado de monitoramento de endereços Bitcoin.
 *
 * <p>Executa a cada 60 segundos, adquire trava distribuída via
 * {@link JobLockService} e chama {@link BitcoinMonitorService#pollAddress}
 * para cada endereço ativo.</p>
 *
 * <p>Habilitado em produção e desabilitado em testes
 * ({@code quarkus.scheduler.enabled=false} no perfil de testes).</p>
 */
@ApplicationScoped
public class BitcoinMonitorJob {

    private static final Logger LOG = Logger.getLogger(BitcoinMonitorJob.class);

    /** Nome do lock distribuído para este job. */
    static final String JOB_NAME = "bitcoin-monitor";

    /** TTL do lock — deve ser maior que a duração esperada do ciclo. */
    static final Duration LOCK_TTL = Duration.ofSeconds(120);

    private final JobLockService jobLockService;
    private final BitcoinMonitorService monitorService;

    /** Identificador único desta instância da aplicação. */
    private final String ownerId = UUID.randomUUID().toString();

    @Inject
    public BitcoinMonitorJob(JobLockService jobLockService, BitcoinMonitorService monitorService) {
        this.jobLockService = jobLockService;
        this.monitorService = monitorService;
    }

    /**
     * Ciclo de polling dos endereços ativos.
     *
     * <p>Executa a cada 60 segundos. O atraso inicial de 10 segundos permite
     * que a aplicação esteja completamente inicializada antes do primeiro ciclo.</p>
     */
    @Scheduled(every = "60s", identity = JOB_NAME)
    public void poll() {
        if (!jobLockService.acquire(JOB_NAME, ownerId, LOCK_TTL)) {
            LOG.debugf("Lock do job '%s' detido por outra instância — ciclo ignorado", JOB_NAME);
            return;
        }

        try {
            try (CorrelationIdContext.Scope ignored = CorrelationIdContext.openNew()) {
                runPollCycle();
            }
        } finally {
            jobLockService.release(JOB_NAME, ownerId);
        }
    }

    /**
     * Executa o ciclo de polling para todos os endereços ativos.
     * Exposto com visibilidade de pacote para facilitar testes unitários.
     */
    void runPollCycle() {
        List<Address> addresses = monitorService.getActiveAddresses();

        if (addresses.isEmpty()) {
            LOG.debugf("Nenhum endereço ativo para monitorar.");
            return;
        }

        LOG.infof("Iniciando ciclo de monitoramento Bitcoin para %d endereço(s).", addresses.size());

        int successCount = 0;
        int failCount    = 0;

        for (Address address : addresses) {
            try {
                monitorService.pollAddress(address);
                successCount++;
            } catch (Exception e) {
                failCount++;
                LOG.errorf(e, "Falha ao monitorar endereço=%s — continua para o próximo",
                        address.canonical);
            }
        }

        LOG.infof("Ciclo Bitcoin concluído: %d sucesso(s), %d falha(s).", successCount, failCount);
    }
}
