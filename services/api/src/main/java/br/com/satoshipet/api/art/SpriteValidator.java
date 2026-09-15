package br.com.satoshipet.api.art;

import jakarta.enterprise.context.ApplicationScoped;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validação técnica de sprites (ART-04, CA-039).
 */
@ApplicationScoped
public class SpriteValidator {

    public record ValidationResult(boolean valid, String rejectionReason, List<BufferedImage> frames) {
        public static ValidationResult ok(List<BufferedImage> frames) {
            return new ValidationResult(true, null, frames);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason, List.of());
        }
    }

    public ValidationResult validateAtlas(byte[] atlasPng) {
        if (atlasPng == null || atlasPng.length < 8) {
            return ValidationResult.reject("empty_atlas");
        }
        if (!isPng(atlasPng)) {
            return ValidationResult.reject("not_png");
        }
        BufferedImage atlas;
        try {
            atlas = ImageIO.read(new ByteArrayInputStream(atlasPng));
        } catch (IOException e) {
            return ValidationResult.reject("decode_failed");
        }
        if (atlas == null) {
            return ValidationResult.reject("decode_failed");
        }
        int expectedW = SpritePose.ATLAS_COLUMNS * SpritePose.FRAME_SIZE_PX;
        int expectedH = SpritePose.ATLAS_ROWS * SpritePose.FRAME_SIZE_PX;
        if (atlas.getWidth() != expectedW || atlas.getHeight() != expectedH) {
            return ValidationResult.reject("atlas_dimensions");
        }
        if (!atlas.getColorModel().hasAlpha()) {
            return ValidationResult.reject("missing_alpha");
        }

        List<BufferedImage> frames = new ArrayList<>();
        List<Set<Integer>> palettes = new ArrayList<>();
        int index = 0;
        for (SpritePose ignored : SpritePose.atlasOrder()) {
            int col = index % SpritePose.ATLAS_COLUMNS;
            int row = index / SpritePose.ATLAS_COLUMNS;
            BufferedImage frame = atlas.getSubimage(
                    col * SpritePose.FRAME_SIZE_PX,
                    row * SpritePose.FRAME_SIZE_PX,
                    SpritePose.FRAME_SIZE_PX,
                    SpritePose.FRAME_SIZE_PX
            );
            if (!hasVisiblePixels(frame)) {
                return ValidationResult.reject("empty_frame");
            }
            if (!hasTransparency(frame)) {
                return ValidationResult.reject("frame_opaque");
            }
            frames.add(frame);
            palettes.add(dominantColors(frame));
            index++;
        }

        Set<Integer> reference = palettes.getFirst();
        for (Set<Integer> palette : palettes) {
            if (jaccard(reference, palette) < 0.35) {
                return ValidationResult.reject("palette_mismatch");
            }
        }
        return ValidationResult.ok(frames);
    }

    private static boolean isPng(byte[] data) {
        return data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47;
    }

    private static boolean hasVisiblePixels(BufferedImage frame) {
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if ((frame.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTransparency(BufferedImage frame) {
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if ((frame.getRGB(x, y) >>> 24) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<Integer> dominantColors(BufferedImage frame) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < frame.getHeight(); y += 2) {
            for (int x = 0; x < frame.getWidth(); x += 2) {
                int argb = frame.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int rgb = argb & 0x00FFFFFF;
                colors.add(rgb);
            }
        }
        return colors;
    }

    private static double jaccard(Set<Integer> a, Set<Integer> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<Integer> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<Integer> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
