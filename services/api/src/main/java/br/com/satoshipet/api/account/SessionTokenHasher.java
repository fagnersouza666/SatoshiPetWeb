package br.com.satoshipet.api.account;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Produz o verificador SHA-256 de tokens de sessão e CSRF sem expor o segredo.
 *
 * <p>Segue o mesmo padrão de {@link MagicLinkTokenHasher}: o token bruto
 * nunca é persistido — somente seu hash SHA-256 em hexadecimal.</p>
 */
@ApplicationScoped
public class SessionTokenHasher {

    private static final HexFormat HEX = HexFormat.of();

    /**
     * Calcula o hash SHA-256 do token informado.
     *
     * @param rawToken token bruto (não pode ser nulo nem vazio)
     * @return hash SHA-256 em hexadecimal (64 caracteres)
     */
    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        if (rawToken.isEmpty()) {
            throw new IllegalArgumentException("rawToken não pode ser vazio");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
