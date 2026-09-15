package br.com.satoshipet.api.art.generation;

import br.com.satoshipet.api.art.FrozenGenerationContext;

/**
 * Pedido de geração enviado ao provedor (ART-02).
 * O prompt permanece privado no servidor.
 */
public record GenerationRequest(
        String promptPrivate,
        FrozenGenerationContext context,
        String seed,
        StubMode stubMode
) {
    public enum StubMode {
        NORMAL,
        TECHNICAL_FAILURE,
        INVALID_OUTPUT,
        CONTENT_BLOCKED
    }

    public GenerationRequest withStubMode(StubMode mode) {
        return new GenerationRequest(promptPrivate, context, seed, mode);
    }

    public static GenerationRequest normal(String prompt, FrozenGenerationContext context, String seed) {
        return new GenerationRequest(prompt, context, seed, StubMode.NORMAL);
    }
}
