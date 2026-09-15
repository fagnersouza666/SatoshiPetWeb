package br.com.satoshipet.api.art;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Bloco de arte no snapshot autenticado (ART-05, ART-06). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArtworkInfoResponse(
        String generationStatus,
        Boolean canApprove,
        Boolean canRegenerate,
        Map<String, String> previewUrls,
        Integer approvedVersion,
        String atlasUrl,
        Integer currentAttemptNo
) {
}
