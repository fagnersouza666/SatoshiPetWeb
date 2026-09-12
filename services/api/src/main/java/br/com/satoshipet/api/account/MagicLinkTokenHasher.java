package br.com.satoshipet.api.account;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Produz o verificador persistido sem expor o segredo do link. */
@ApplicationScoped
public class MagicLinkTokenHasher {

    private static final HexFormat HEX = HexFormat.of();

    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        if (rawToken.isEmpty()) {
            throw new IllegalArgumentException("rawToken não pode ser vazio");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
