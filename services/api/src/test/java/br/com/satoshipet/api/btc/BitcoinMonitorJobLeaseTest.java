package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.job.JobLockService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BitcoinMonitorJobLeaseTest {
    @Test
    void interrompeAntesDoProximoEnderecoQuandoPerdeLease() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger polls = new AtomicInteger();
        JobLockService locks = new JobLockService(null, null) {
            @Override public boolean runWhileOwned(String job, String owner, Duration ttl, Runnable work) {
                attempts.incrementAndGet();
                return false;
            }
        };
        BitcoinMonitorService monitor = new BitcoinMonitorService(null, null, null, null) {
            @Override public List<Address> getActiveAddresses() {
                return List.of(Address.create("bc1qfirst", Instant.now()),
                        Address.create("bc1qsecond", Instant.now()));
            }
            @Override public void pollAddress(Address address) { polls.incrementAndGet(); }
        };

        new BitcoinMonitorJob(locks, monitor).runPollCycle("lost-owner");

        assertEquals(1, attempts.get());
        assertEquals(0, polls.get());
    }

    @Test
    void cadaCicloAdquireELiberaComIdentidadePropria() {
        List<String> acquired = new ArrayList<>();
        List<String> released = new ArrayList<>();
        JobLockService locks = new JobLockService(null, null) {
            @Override public boolean acquire(String job, String owner, Duration ttl) {
                acquired.add(owner);
                return true;
            }
            @Override public void release(String job, String owner) { released.add(owner); }
        };
        BitcoinMonitorService monitor = new BitcoinMonitorService(null, null, null, null) {
            @Override public List<Address> getActiveAddresses() { return List.of(); }
        };
        BitcoinMonitorJob job = new BitcoinMonitorJob(locks, monitor);

        job.poll();
        job.poll();

        assertEquals(2, acquired.size());
        assertNotEquals(acquired.get(0), acquired.get(1));
        assertEquals(acquired, released);
    }
}
