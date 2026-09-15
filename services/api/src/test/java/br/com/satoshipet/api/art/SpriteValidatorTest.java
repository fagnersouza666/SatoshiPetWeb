package br.com.satoshipet.api.art;

import br.com.satoshipet.api.art.generation.StubImageGeneration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteValidatorTest {

    private final SpriteValidator validator = new SpriteValidator();

    @Test
    void aceitaAtlasStubValido() throws Exception {
        byte[] atlas = StubImageGeneration.buildValidAtlas("valid-seed");
        assertTrue(validator.validateAtlas(atlas).valid());
    }

    @Test
    void rejeitaBytesInvalidos() {
        assertFalse(validator.validateAtlas(new byte[] {1, 2, 3}).valid());
    }
}
