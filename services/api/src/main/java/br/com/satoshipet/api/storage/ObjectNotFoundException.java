package br.com.satoshipet.api.storage;

/** Lançada quando uma chave não existe no object storage. */
public class ObjectNotFoundException extends RuntimeException {

    public ObjectNotFoundException(String key) {
        super("Objeto não encontrado no storage: " + key);
    }
}
