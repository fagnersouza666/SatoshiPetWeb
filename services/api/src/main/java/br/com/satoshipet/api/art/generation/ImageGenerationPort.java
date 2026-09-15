package br.com.satoshipet.api.art.generation;

/**
 * Porta para geração de imagens por IA (ART-02).
 * Implementação de produção futura; recorte atual usa stub determinístico.
 */
public interface ImageGenerationPort {

    GenerationResult generate(GenerationRequest request) throws ImageGenerationException;
}
