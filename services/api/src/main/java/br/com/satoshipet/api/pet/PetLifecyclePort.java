package br.com.satoshipet.api.pet;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta para eventos do ciclo de vida do pet.
 *
 * <p>Somente recebimentos reais on-chain confirmados/pendentes alimentam o
 * pet. Compras declaradas, sugestões DCA ou notificações nunca disparam
 * alimentação (PRD §5, invariante crítico).</p>
 *
 * <p>Pet desconhecido → {@link IllegalArgumentException} ("Pet não encontrado").</p>
 */
public interface PetLifecyclePort {

    /**
     * Recebimento observado (mempool ou já confirmado).
     *
     * @param petId            identificador do pet
     * @param logicalReceiptId recebimento lógico único (CA-017)
     * @param amountSats       sats do recebimento
     * @param confirmed        {@code true} se já confirmado on-chain
     * @param observedAt       instante da observação (UTC)
     */
    void onReceiptObserved(
            UUID petId,
            UUID logicalReceiptId,
            long amountSats,
            boolean confirmed,
            Instant observedAt);

    /**
     * Confirmação on-chain de um recebimento já observado ou inédito.
     */
    void onReceiptConfirmed(UUID petId, UUID logicalReceiptId, long amountSats, Instant confirmedAt);

    /**
     * Revisão de valor (RBF). Quantidade zero equivale a invalidação.
     */
    void onReceiptRevised(UUID petId, UUID logicalReceiptId, long newAmountSats, Instant when);

    /**
     * Invalidação (reorg/drop). Não apaga a linha de alimentação.
     */
    void onReceiptInvalidated(UUID petId, UUID logicalReceiptId, Instant when);

    /**
     * Snapshot de saldo conhecido. Task 6 (carência/ovo) — no-op neste recorte.
     */
    void onBalanceKnown(UUID petId, long confirmedSats, long pendingIncomingSats, Instant when);

    /**
     * Falha de provedor. Não equivale a saldo zero (CA-031); não altera carência.
     */
    void onProviderFailure(UUID petId, Instant when);

    /**
     * Consome a reserva pelo tempo decorrido e atualiza o estado emocional.
     */
    void tick(UUID petId, Instant now);

    /**
     * Reconstrução histórica: replay de recebimentos confirmados com cap 168h
     * (PET-08, PET-10, CA-014, CA-027). Sem porção positiva, permanece no-op.
     */
    void reconstruct(UUID petId, Instant now);

    /**
     * Reconstrução que preserva o {@code awaitingReference} anterior à virada
     * true→false já persistida pelo port da porção (PET-16).
     */
    default void reconstruct(UUID petId, Instant now, boolean awaitingReferenceBefore) {
        reconstruct(petId, now);
    }
}
