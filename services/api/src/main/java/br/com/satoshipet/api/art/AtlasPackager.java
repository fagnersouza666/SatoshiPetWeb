package br.com.satoshipet.api.art;

import br.com.satoshipet.api.storage.ObjectStoragePort;
import br.com.satoshipet.api.storage.StorageNamespace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Empacota atlas e frames individuais no object storage (ART-03). */
@ApplicationScoped
public class AtlasPackager {

    private final ObjectStoragePort storage;

    @Inject
    public AtlasPackager(ObjectStoragePort storage) {
        this.storage = storage;
    }

    public Map<String, byte[]> packageAtlas(byte[] atlasPng, List<BufferedImage> frames, String keyPrefix) {
        Map<String, byte[]> written = new LinkedHashMap<>();
        storage.put(StorageNamespace.STAGING, keyPrefix + "/atlas.png", atlasPng, "image/png");
        written.put("atlas.png", atlasPng);

        int index = 0;
        for (SpritePose pose : SpritePose.atlasOrder()) {
            BufferedImage frame = frames.get(index++);
            byte[] png = encodePng(frame);
            String file = pose.fileName();
            storage.put(StorageNamespace.STAGING, keyPrefix + "/" + file, png, "image/png");
            written.put(file, png);
        }
        return written;
    }

    public List<String> objectKeys(String keyPrefix) {
        return SpritePose.atlasOrder().stream()
                .map(p -> keyPrefix + "/" + p.fileName())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        list -> {
                            java.util.ArrayList<String> keys = new java.util.ArrayList<>();
                            keys.add(keyPrefix + "/atlas.png");
                            keys.addAll(list);
                            return keys;
                        }
                ));
    }

    private static byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao codificar frame PNG", e);
        }
    }
}
