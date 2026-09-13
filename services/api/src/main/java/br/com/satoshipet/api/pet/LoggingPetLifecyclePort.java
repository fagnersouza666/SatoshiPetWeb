package br.com.satoshipet.api.pet;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Helper de teste do ciclo de vida do pet: registra chamadas em memória.
 *
 * <p>Não é bean CDI; a implementação de produção é {@code PetEngine}.</p>
 */
public class LoggingPetLifecyclePort implements PetLifecyclePort {

    private static final Logger LOG = Logger.getLogger(LoggingPetLifecyclePort.class);

    /** Registro das chamadas para verificação em testes. */
    public record FeedingCall(UUID petId, long amountSats, Instant when) {}

    private final List<FeedingCall> feedingCalls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onReceiptObserved(
            UUID petId,
            UUID logicalReceiptId,
            long amountSats,
            boolean confirmed,
            Instant observedAt
    ) {
        LOG.infof("[LoggingPetLifecycle] onReceiptObserved petId=%s receipt=%s sats=%d confirmed=%s",
                petId, logicalReceiptId, amountSats, confirmed);
    }

    @Override
    public void onReceiptConfirmed(UUID petId, UUID logicalReceiptId, long amountSats, Instant confirmedAt) {
        LOG.infof("[LoggingPetLifecycle] onReceiptConfirmed petId=%s receipt=%s sats=%d",
                petId, logicalReceiptId, amountSats);
    }

    @Override
    public void onReceiptRevised(UUID petId, UUID logicalReceiptId, long newAmountSats, Instant when) {
        LOG.infof("[LoggingPetLifecycle] onReceiptRevised petId=%s receipt=%s sats=%d",
                petId, logicalReceiptId, newAmountSats);
    }

    @Override
    public void onReceiptInvalidated(UUID petId, UUID logicalReceiptId, Instant when) {
        LOG.infof("[LoggingPetLifecycle] onReceiptInvalidated petId=%s receipt=%s", petId, logicalReceiptId);
    }

    @Override
    public void onBalanceKnown(UUID petId, long confirmedSats, long pendingIncomingSats, Instant when) {
        LOG.infof("[LoggingPetLifecycle] onBalanceKnown petId=%s confirmed=%d pending=%d",
                petId, confirmedSats, pendingIncomingSats);
    }

    @Override
    public void onProviderFailure(UUID petId, Instant when) {
        LOG.infof("[LoggingPetLifecycle] onProviderFailure petId=%s", petId);
    }

    @Override
    public void tick(UUID petId, Instant now) {
        LOG.infof("[LoggingPetLifecycle] tick petId=%s now=%s", petId, now);
    }

    @Override
    public void reconstruct(UUID petId, Instant now) {
        LOG.infof("[LoggingPetLifecycle] reconstruct petId=%s", petId);
    }

    @Override
    @Deprecated
    public void applyFeeding(UUID petId, long amountSats, Instant when) {
        feedingCalls.add(new FeedingCall(petId, amountSats, when));
        LOG.infof("[LoggingPetLifecycle] applyFeeding petId=%s sats=%d when=%s",
                petId, amountSats, when);
    }

    /** Retorna cópia imutável das chamadas de alimentação registradas. */
    public List<FeedingCall> getFeedingCalls() {
        return List.copyOf(feedingCalls);
    }

    /** Limpa os registros (útil entre testes). */
    public void reset() {
        feedingCalls.clear();
    }
}
