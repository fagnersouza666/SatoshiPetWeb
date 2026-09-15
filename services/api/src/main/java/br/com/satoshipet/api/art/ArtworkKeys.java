package br.com.satoshipet.api.art;

import java.util.UUID;

/** Chaves versionadas no object storage (ART-10). */
public final class ArtworkKeys {

    private ArtworkKeys() {
    }

    public static String stagingPrefix(UUID petId, int version, int attemptNo) {
        return "pets/" + petId + "/v" + version + "/attempt-" + attemptNo;
    }

    public static String approvedPrefix(UUID petId, int version) {
        return "pets/" + petId + "/v" + version;
    }

    public static String publicAtlasPath(String canonical, int version) {
        return "/api/v1/public/addresses/" + canonical + "/artwork/" + version + "/atlas.png";
    }
}
