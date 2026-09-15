package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.outbox.OutboxConsumer;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.pet.Pet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Dispara geração inicial no nascimento (ART-02, idempotente). */
@ApplicationScoped
public class ArtworkGenerationConsumer implements OutboxConsumer {

    private static final Logger LOG = Logger.getLogger(ArtworkGenerationConsumer.class);

    private final ArtworkPipeline pipeline;
    private final ObjectMapper objectMapper;

    @Inject
    public ArtworkGenerationConsumer(ArtworkPipeline pipeline, ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return "PET_BORN".equals(eventType);
    }

    @Override
    @Transactional
    public void consume(OutboxEvent event) {
        String canonical = resolveAddress(event);
        if (canonical == null) {
            LOG.warnf("PET_BORN sem endereço resolvível id=%s", event.id);
            return;
        }
        Optional<Address> address = Address.findByCanonical(canonical);
        if (address.isEmpty()) {
            return;
        }
        Optional<Pet> pet = Pet.findByAddress(address.get());
        if (pet.isEmpty()) {
            return;
        }
        pipeline.enqueueInitial(pet.get(), Instant.now());
    }

    private String resolveAddress(OutboxEvent event) {
        if ("Address".equals(event.aggregateType)) {
            return event.aggregateId;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    event.payload,
                    new TypeReference<>() {}
            );
            Object address = payload.get("address");
            return address instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }
}
