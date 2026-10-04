package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.job.JobLockService;
import br.com.satoshipet.api.platform.CorrelationIdContext;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Correlação de todo o ciclo do job, incluindo falhas e liberação da trava. */
class PetTickJobCorrelationTest {
    private final List<Stage> stages = new ArrayList<>();
    private PetTickJob job;
    private boolean available = true;
    private boolean failAcquire;
    private boolean failWork;
    private boolean failRelease;

    @BeforeEach
    void preparaJobIsolado() {
        JobLockService locks = mock(JobLockService.class);
        job = spy(new PetTickJob(locks, mock(PetLifecyclePort.class)));
        when(locks.acquire(anyString(), anyString(), any())).thenAnswer(invocation -> {
            record("acquire");
            if (failAcquire) throw new IllegalStateException("falha na aquisição");
            return available;
        });
        doAnswer(invocation -> {
            record("work");
            if (failWork) throw new IllegalStateException("falha no processamento");
            return null;
        }).when(job).runTickCycle(anyString());
        doAnswer(invocation -> {
            record("release");
            if (failRelease) throw new IllegalStateException("falha na liberação");
            return null;
        }).when(locks).release(anyString(), anyString());
    }

    @AfterEach
    void limpaContexto() {
        MDC.remove(CorrelationIdContext.MDC_KEY);
    }

    @Test
    void compartilhaNovoIdEntreAquisicaoProcessamentoELiberacao() {
        inOuterContext(() -> job.tick());
        assertStages("acquire", "work", "release");
    }

    @Test
    void preservaIdDuranteFalhaDeProcessamentoELiberacao() {
        failWork = true;
        inOuterContext(() -> assertThrows(IllegalStateException.class, () -> job.tick()));
        assertStages("acquire", "work", "release");
    }

    @Test
    void restauraContextoQuandoAquisicaoFalha() {
        failAcquire = true;
        inOuterContext(() -> assertThrows(IllegalStateException.class, () -> job.tick()));
        assertStages("acquire");
    }

    @Test
    void restauraContextoQuandoLiberacaoFalha() {
        failRelease = true;
        inOuterContext(() -> assertThrows(IllegalStateException.class, () -> job.tick()));
        assertStages("acquire", "work", "release");
    }

    @Test
    void ciclosSemTravaNaoProcessamNemReutilizamId() {
        available = false;
        inOuterContext(() -> job.tick());
        assertStages("acquire");
        String firstId = stages.getFirst().correlationId();
        stages.clear();
        inOuterContext(() -> job.tick());
        assertStages("acquire");
        assertNotEquals(firstId, stages.getFirst().correlationId());
    }

    private void record(String phase) {
        stages.add(new Stage(phase, CorrelationIdContext.current()));
    }

    private void inOuterContext(Runnable action) {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open("outer-operation")) {
            action.run();
            assertEquals("outer-operation", CorrelationIdContext.current());
        }
        assertNull(CorrelationIdContext.current());
    }

    private void assertStages(String... expected) {
        assertEquals(List.of(expected), stages.stream().map(Stage::phase).toList());
        String correlationId = stages.getFirst().correlationId();
        assertNotNull(correlationId);
        assertNotEquals("outer-operation", correlationId);
        assertNotNull(UUID.fromString(correlationId));
        assertTrue(stages.stream().allMatch(stage -> correlationId.equals(stage.correlationId())));
    }

    private record Stage(String phase, String correlationId) {}
}
