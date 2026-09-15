package br.com.satoshipet.api.art;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guardrails de conteúdo para prompts (PRD §8.1, ART-09).
 */
@ApplicationScoped
public class ContentGuardrails {

    private static final List<Pattern> BLOCKED = List.of(
            Pattern.compile("\\bnike\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcoca[- ]?cola\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsexual\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgore\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcelebridade\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpessoa real\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * @return código de bloqueio ou {@code null} se permitido
     */
    public String blockedReason(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "empty_prompt";
        }
        String normalized = prompt.toLowerCase(Locale.ROOT);
        for (Pattern pattern : BLOCKED) {
            if (pattern.matcher(normalized).find()) {
                return "content_blocked";
            }
        }
        return null;
    }
}
