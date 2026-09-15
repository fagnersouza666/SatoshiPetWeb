package br.com.satoshipet.api.art;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class ContentGuardrailsTest {

    @Inject
    ContentGuardrails guardrails;

    @Test
    void permitePromptPadraoDoContexto() {
        String prompt = new ArtworkContextBuilder().buildPrompt(new FrozenGenerationContext(
                "bc1qtest",
                "America/Sao_Paulo",
                "desconhecida",
                "indisponivel",
                "afternoon",
                "2026-09-14T12:00:00Z"
        ));
        assertNull(guardrails.blockedReason(prompt));
    }

    @Test
    void bloqueiaMarcasEConteudoProibido() {
        assertEquals("content_blocked", guardrails.blockedReason("criatura estilo nike"));
        assertEquals("empty_prompt", guardrails.blockedReason(" "));
    }
}
