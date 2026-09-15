package br.com.satoshipet.api.job;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica a exclusão mútua do JobLock contra um PostgreSQL real. */
@QuarkusTest
@Testcontainers(disabledWithoutDocker = true)
@QuarkusTestResource(value = PostgreSqlJobLockTestResource.class, restrictToAnnotatedClass = true)
class JobLockServicePostgreSqlTest {

    private static final Duration LOCK_TTL = Duration.ofMinutes(1);

    @Inject
    JobLockService jobLockService;

    @Inject
    DataSource dataSource;

    @Test
    void duasInstanciasDisputamPeloMesmoJobESomenteUmaAdquire() throws Exception {
        String jobName = "postgres-lock-concurrent-" + UUID.randomUUID();
        String instanceA = "api-instance-a-" + UUID.randomUUID();
        String instanceB = "api-instance-b-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> resultA = executor.submit(() -> tentaAdquirir(start, jobName, instanceA));
            Future<Boolean> resultB = executor.submit(() -> tentaAdquirir(start, jobName, instanceB));
            start.countDown();

            boolean acquiredA = await(resultA);
            boolean acquiredB = await(resultB);

            assertTrue(acquiredA ^ acquiredB,
                    "Exatamente uma instância deve adquirir o lock no PostgreSQL");

            String winner = acquiredA ? instanceA : instanceB;
            assertLockOwner(jobName, winner);
            jobLockService.release(jobName, winner);
            assertEquals(0, countLocks(jobName),
                    "A instância vencedora deve liberar o lock ao concluir o ciclo");
        }
    }

    private boolean tentaAdquirir(CountDownLatch start, String jobName, String ownerId)
            throws InterruptedException {
        assertTrue(start.await(10, TimeUnit.SECONDS),
                "As duas instâncias devem iniciar a disputa dentro do timeout");
        return jobLockService.acquire(jobName, ownerId, LOCK_TTL);
    }

    private boolean await(Future<Boolean> result)
            throws InterruptedException, ExecutionException, TimeoutException {
        return result.get(10, TimeUnit.SECONDS);
    }

    private void assertLockOwner(String jobName, String expectedOwner) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT owner_id FROM job_locks WHERE job_name = ?")) {
            statement.setString(1, jobName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "A disputa deve deixar um único lock persistido");
                assertEquals(expectedOwner, result.getString("owner_id"));
                assertTrue(!result.next(), "A chave do job deve ter uma única linha");
            }
        }
    }

    private int countLocks(String jobName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM job_locks WHERE job_name = ?")) {
            statement.setString(1, jobName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }
}
