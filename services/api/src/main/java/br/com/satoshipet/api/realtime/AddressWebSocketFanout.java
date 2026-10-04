package br.com.satoshipet.api.realtime;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Cada réplica distribui a projeção durável somente às suas conexões abertas. */
@ApplicationScoped
public class AddressWebSocketFanout {
    @Inject AddressWebSocket addressWebSocket;

    @Scheduled(every = "1s", identity = "ws-address-fanout", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void poll() {
        addressWebSocket.pollCommittedEvents();
    }
}
