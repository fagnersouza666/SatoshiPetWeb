package br.com.satoshipet.api.art.generation;

/** Resultado bruto do provedor de imagens (ART-02). */
public record GenerationResult(byte[] atlasPng, String modelId) {
}
