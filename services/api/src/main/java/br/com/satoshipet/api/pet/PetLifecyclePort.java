package br.com.satoshipet.api.pet;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta para eventos do ciclo de vida do pet.
 *
 * <p>Somente recebimentos reais on-chain confirmados/pendentes acionam
 * {@link #applyFeeding}. Compras declaradas, sugestões DCA ou notificações
 * nunca disparam alimentação (PRD §5, invariante crítico).</p>
 */
public interface PetLifecyclePort {

    /**
     * Registra uma alimentação causada por recebimento on-chain.
     * Nunca deve ser chamado por evento de compra declarada ou sugestão DCA.
     *
     * @param petId      identificador do pet
     * @param amountSats quantidade de sats recebidos
     * @param when       momento do recebimento (UTC)
     */
    void applyFeeding(UUID petId, long amountSats, Instant when);

    /**
     * Atualiza o estado de exibição do pet.
     * Estados válidos: ALIMENTADO, PENSANDO, CHATEADO, FAMINTO, CRITICO, HIBERNANDO.
     *
     * @param petId    identificador do pet
     * @param state    novo estado
     * @param when     momento da transição (UTC)
     */
    void updateState(UUID petId, String state, Instant when);
}
