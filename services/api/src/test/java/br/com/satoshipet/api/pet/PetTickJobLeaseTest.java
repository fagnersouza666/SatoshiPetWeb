package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.job.JobLockService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PetTickJobLeaseTest {
    @Test
    void cadaInvocacaoTemOwnerProprio() {
        List<String> acquiredBy = new ArrayList<>();
        List<String> releasedBy = new ArrayList<>();
        JobLockService locks = new JobLockService(null, null) {
            @Override
            public boolean acquire(String name, String owner, Duration ttl) {
                acquiredBy.add(owner);
                return true;
            }

            @Override
            public void release(String name, String owner) {
                releasedBy.add(owner);
            }
        };
        PetTickJob job = new PetTickJob(locks, null) {
            @Override
            void runTickCycle(String owner) {}
        };

        job.tick();
        job.tick();

        assertEquals(2, acquiredBy.size());
        assertNotEquals(acquiredBy.get(0), acquiredBy.get(1),
                "Uma invocação antiga não pode renovar ou liberar a lease de outro ciclo");
        assertEquals(acquiredBy, releasedBy);
    }
}
