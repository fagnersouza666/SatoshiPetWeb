package br.com.satoshipet.api.art;

/** Erro de negócio na aprovação/regeneração (ART-05). */
public class ArtworkOperationException extends RuntimeException {

    private final String code;

    public ArtworkOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
