package br.com.satoshipet.api.art.generation;

/** Falha técnica ou bloqueio do provedor de imagens (ART-08, ART-09). */
public class ImageGenerationException extends Exception {

    private final String code;

    public ImageGenerationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
