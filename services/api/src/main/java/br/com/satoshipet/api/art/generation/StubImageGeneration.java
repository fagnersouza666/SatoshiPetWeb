package br.com.satoshipet.api.art.generation;

import br.com.satoshipet.api.art.SpritePose;
import jakarta.enterprise.context.ApplicationScoped;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Stub determinístico de geração: atlas 4×4 coerente (ART-02, CA-036).
 */
@ApplicationScoped
public class StubImageGeneration implements ImageGenerationPort {

    static final String MODEL_ID = "stub-v1";

    @Override
    public GenerationResult generate(GenerationRequest request) throws ImageGenerationException {
        if (request.stubMode() == GenerationRequest.StubMode.TECHNICAL_FAILURE) {
            throw new ImageGenerationException("provider_failure", "Stub simulou falha técnica");
        }
        if (request.stubMode() == GenerationRequest.StubMode.CONTENT_BLOCKED) {
            throw new ImageGenerationException("content_blocked", "Stub simulou bloqueio de conteúdo");
        }
        if (request.stubMode() == GenerationRequest.StubMode.INVALID_OUTPUT) {
            try {
                return new GenerationResult(buildInvalidAtlas(), MODEL_ID);
            } catch (IOException e) {
                throw new ImageGenerationException("encode_failure", "Falha ao codificar atlas inválido stub");
            }
        }
        try {
            return new GenerationResult(buildValidAtlas(request.seed()), MODEL_ID);
        } catch (IOException e) {
            throw new ImageGenerationException("encode_failure", "Falha ao codificar atlas stub");
        }
    }

    public static byte[] buildValidAtlas(String seed) throws IOException {
        int frame = SpritePose.FRAME_SIZE_PX;
        int width = SpritePose.ATLAS_COLUMNS * frame;
        int height = SpritePose.ATLAS_ROWS * frame;
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, width, height);

        Color base = colorFromSeed(seed);
        int index = 0;
        for (SpritePose ignored : SpritePose.atlasOrder()) {
            int col = index % SpritePose.ATLAS_COLUMNS;
            int row = index / SpritePose.ATLAS_COLUMNS;
            drawCreature(g, col * frame, row * frame, frame, base, index);
            index++;
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", out);
        return out.toByteArray();
    }

    private static byte[] buildInvalidAtlas() throws IOException {
        BufferedImage broken = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = broken.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(broken, "png", out);
        return out.toByteArray();
    }

    private static void drawCreature(Graphics2D g, int x, int y, int size, Color base, int poseIndex) {
        int body = size - 12;
        int offset = (poseIndex % 3) - 1;
        Color bodyColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 255);
        Color eyeColor = new Color(
                Math.min(255, base.getRed() + 20),
                Math.min(255, base.getGreen() + 10),
                base.getBlue(),
                255
        );
        g.setColor(bodyColor);
        g.fillOval(x + 4 + offset, y + 8, body, body);
        g.setColor(eyeColor);
        g.fillOval(x + 10 + offset, y + 12, 5, 5);
        g.fillOval(x + 18 + offset, y + 12, 5, 5);
    }

    static Color colorFromSeed(String seed) {
        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));
        long value = crc.getValue();
        int r = (int) ((value >> 16) & 0xFF);
        int g = (int) ((value >> 8) & 0xFF);
        int b = (int) (value & 0xFF);
        return new Color(Math.max(80, r), Math.max(80, g), Math.max(80, b));
    }
}
