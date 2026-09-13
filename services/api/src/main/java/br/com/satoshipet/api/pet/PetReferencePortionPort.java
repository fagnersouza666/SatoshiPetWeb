package br.com.satoshipet.api.pet;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolve e registra a porção de referência única do pet (CC-05, CC-11).
 *
 * <p>Não reconstrói alimentações históricas. Sem snapshot e sem última porção
 * positiva, devolve {@link Optional#empty()} (aguardando referência).</p>
 */
public interface PetReferencePortionPort {

    record ResolvedPortion(long portionSats, PortionOrigin origin, UUID sourceAccountId) {}

    Optional<ResolvedPortion> currentPositivePortion(UUID petId);

    /**
     * Persiste snapshot imutável. Atualiza lastPositivePortionSats / origin
     * e awaitingReference=false somente se a conta fonte for a fonte alimentar
     * ativa após refresh (CC-05). portionSats deve ser &gt; 0.
     * Não reconstrói alimentações históricas.
     *
     * @param sourceAccountId conta da snapshot; pode ser nulo
     */
    void recordPositivePortion(
            UUID petId,
            UUID sourceAccountId,
            long portionSats,
            PortionOrigin origin,
            Instant validFrom);

    /**
     * Se a fonte alimentar não estiver vinculada ao endereço do pet, troca para
     * o vínculo ativo mais antigo ({@code boundAt} ASC, depois {@code id} ASC).
     * Não inventa porção. Conta recém-vinculada não reinterpreta o histórico.
     */
    void refreshFoodSource(UUID petId);
}
