package br.com.satoshipet.api.auth.magiclink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicLinkContractTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aceitaSomenteUmEmailValidoNoRequest() {
        Set<ConstraintViolation<MagicLinkRequest>> violations = validator.validate(
                new MagicLinkRequest("pessoa@example.com")
        );

        assertTrue(violations.isEmpty());
    }

    @Test
    void rejeitaEmailAusenteInvalidoOuLongo() {
        Set<ConstraintViolation<MagicLinkRequest>> violations = validator.validate(
                new MagicLinkRequest(" ")
        );

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().allMatch(violation ->
                "email".equals(violation.getPropertyPath().toString())));

        Set<ConstraintViolation<MagicLinkRequest>> formatViolations = validator.validate(
                new MagicLinkRequest("nao-e-um-email")
        );
        assertTrue(formatViolations.stream().anyMatch(violation ->
                violation.getConstraintDescriptor().getAnnotation() instanceof Email));

        String domain = "@x.com";
        String longEmail = "a".repeat(MagicLinkRequest.MAX_EMAIL_LENGTH - domain.length() + 1) + domain;
        Set<ConstraintViolation<MagicLinkRequest>> lengthViolations = validator.validate(
                new MagicLinkRequest(longEmail)
        );

        assertTrue(lengthViolations.stream().anyMatch(violation ->
                violation.getConstraintDescriptor().getAnnotation() instanceof Size));
    }

    @Test
    void respostaAceitaNaoExpoeTokenOuExpiracao() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                MagicLinkRequestResponse.accepted()
        ));

        assertEquals("accepted", json.get("status").asText());
        assertEquals(MagicLinkRequestResponse.ACCEPTED_MESSAGE, json.get("message").asText());
        assertFalse(json.has("token"));
        assertFalse(json.has("expiresAt"));
    }

    @Test
    void erroDeValidacaoMantemCamposEEscondeValorInformado() throws Exception {
        MagicLinkErrorResponse response = MagicLinkErrorResponse.invalidRequest(List.of(
                new MagicLinkFieldError(
                        "email",
                        MagicLinkFieldErrorCode.INVALID_FORMAT,
                        "Informe um e-mail válido."
                )
        ));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("invalid_request", json.get("code").asText());
        assertNotNull(json.get("fieldErrors"));
        assertEquals("email", json.get("fieldErrors").get(0).get("field").asText());
        assertEquals("invalid_format", json.get("fieldErrors").get(0).get("code").asText());
        assertFalse(json.toString().contains("pessoa@example.com"));

        JsonNode rateLimited = objectMapper.readTree(objectMapper.writeValueAsString(
                MagicLinkErrorResponse.rateLimited()
        ));
        assertFalse(rateLimited.has("fieldErrors"));
    }
}
