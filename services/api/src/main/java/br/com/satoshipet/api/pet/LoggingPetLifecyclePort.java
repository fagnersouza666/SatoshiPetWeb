package br.com.satoshipet.api.pet;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Stub do ciclo de vida do pet: registra chamadas em log e em lista em memória.
 *
 * <p>Bean padrão enquanto a implementação real (épico PET) não existir.
 * A lista de chamadas é útil para verificar invariantes em testes.</p>
 */
@ApplicationScoped
@DefaultBean
public class LoggingPetLifecyclePort implements PetLifecyclePort {

    private static final Logger LOG = Logger.getLogger(LoggingPetLifecyclePort.class);

    /** Registro das chamadas para verificação em testes. */
    public record FeedingCall(UUID petId, long amountSats, Instant when) {}
    public record StateCall(UUID petId, String state, Instant when) {}

    private final List<FeedingCall> feedingCalls = Collections.synchronizedList(new ArrayList<>());
    private final List<StateCall> stateCalls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void applyFeeding(UUID petId, long amountSats, Instant when) {
        feedingCalls.add(new FeedingCall(petId, amountSats, when));
        LOG.infof("[LoggingPetLifecycle] applyFeeding petId=%s sats=%d when=%s",
                petId, amountSats, when);
    }

    @Override
    public void updateState(UUID petId, String state, Instant when) {
        stateCalls.add(new StateCall(petId, state, when));
        LOG.infof("[LoggingPetLifecycle] updateState petId=%s state=%s when=%s",
                petId, state, when);
    }

    /** Retorna cópia imutável das chamadas de alimentação registradas. */
    public List<FeedingCall> getFeedingCalls() {
        return List.copyOf(feedingCalls);
    }

    /** Retorna cópia imutável das chamadas de estado registradas. */
    public List<StateCall> getStateCalls() {
        return List.copyOf(stateCalls);
    }

    /** Limpa os registros (útil entre testes). */
    public void reset() {
        feedingCalls.clear();
        stateCalls.clear();
    }
}
