package br.com.satoshipet.api.art;

/** Estado detalhado do job de geração de arte (ART-01). */
public enum ArtGenerationStatus {
    GENERATING,
    AWAITING_APPROVAL,
    APPROVED,
    RETRY_WAIT
}
