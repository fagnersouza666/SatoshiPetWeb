package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.storage.ObjectStoragePort;
import br.com.satoshipet.api.storage.StorageNamespace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Operações de leitura e mutação da arte (ART-05, ART-07). */
@ApplicationScoped
public class ArtworkService {

    private final ArtworkPipeline pipeline;
    private final ObjectStoragePort storage;

    @Inject
    public ArtworkService(ArtworkPipeline pipeline, ObjectStoragePort storage) {
        this.pipeline = pipeline;
        this.storage = storage;
    }

    public Optional<ArtworkInfoResponse> artworkInfo(Pet pet, Account viewer) {
        Optional<PetArtwork> artworkOpt = PetArtwork.findByPet(pet);
        if (artworkOpt.isEmpty()) {
            return Optional.empty();
        }
        PetArtwork artwork = artworkOpt.get();
        boolean creator = isCreatorBound(viewer, pet);
        boolean awaiting = artwork.generationStatus == ArtGenerationStatus.AWAITING_APPROVAL;
        boolean approved = artwork.generationStatus == ArtGenerationStatus.APPROVED;

        Map<String, String> preview = null;
        if (creator && awaiting) {
            PetArtworkAttempt latest = latestValidAttempt(artwork);
            if (latest != null) {
                preview = previewUrls(latest.attemptNo);
            }
        }

        String atlasUrl = approved && artwork.assetVersion > 0
                ? ArtworkKeys.publicAtlasPath(pet.address.canonical, artwork.assetVersion)
                : null;

        Integer attemptNo = latestValidAttempt(artwork) == null
                ? null
                : latestValidAttempt(artwork).attemptNo;

        return Optional.of(new ArtworkInfoResponse(
                artwork.generationStatus.name(),
                creator && awaiting,
                creator && awaiting && !artwork.voluntaryRegenUsed,
                preview,
                approved ? artwork.assetVersion : null,
                atlasUrl,
                attemptNo
        ));
    }

    @Transactional
    public ArtworkInfoResponse approve(Account account, Pet pet) {
        ensureCreator(account, pet);
        PetArtwork artwork = requireArtwork(pet);
        pipeline.approve(artwork, Instant.now());
        return artworkInfo(pet, account).orElseThrow();
    }

    @Transactional
    public ArtworkInfoResponse regenerate(Account account, Pet pet) {
        ensureCreator(account, pet);
        PetArtwork artwork = requireArtwork(pet);
        pipeline.requestVoluntaryRegeneration(artwork, Instant.now());
        return artworkInfo(pet, account).orElseThrow();
    }

    public byte[] readStagingObject(Pet pet, Account account, String fileName) {
        ensureCreator(account, pet);
        PetArtwork artwork = requireArtwork(pet);
        PetArtworkAttempt latest = latestValidAttempt(artwork);
        if (latest == null) {
            throw new ArtworkOperationException("not_ready", "Preview indisponível");
        }
        String key = latest.storageKeyPrefix + "/" + sanitizeFileName(fileName);
        return readBytes(StorageNamespace.STAGING, key);
    }

    public byte[] readApprovedObject(Pet pet, int version, String fileName) {
        PetArtwork artwork = requireArtwork(pet);
        if (artwork.generationStatus != ArtGenerationStatus.APPROVED || artwork.assetVersion != version) {
            throw new ArtworkOperationException("not_found", "Versão não aprovada");
        }
        String key = ArtworkKeys.approvedPrefix(pet.id, version) + "/" + sanitizeFileName(fileName);
        return readBytes(StorageNamespace.APPROVED, key);
    }

    private byte[] readBytes(StorageNamespace namespace, String key) {
        try (InputStream stream = storage.get(namespace, key)) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new ArtworkOperationException("not_found", "Asset não encontrado");
        }
    }

    private static PetArtwork requireArtwork(Pet pet) {
        return PetArtwork.findByPet(pet)
                .orElseThrow(() -> new ArtworkOperationException("not_found", "Arte não encontrada"));
    }

    private static void ensureCreator(Account account, Pet pet) {
        if (!pet.creatorAccount.id.equals(account.id)
                || !AccountAddressBinding.isActivelyBound(account, pet.address)) {
            throw new ArtworkOperationException("not_creator", "Apenas o criador vinculado pode gerenciar a arte");
        }
    }

    private static boolean isCreatorBound(Account account, Pet pet) {
        return pet.creatorAccount.id.equals(account.id)
                && AccountAddressBinding.isActivelyBound(account, pet.address);
    }

    private static PetArtworkAttempt latestValidAttempt(PetArtwork artwork) {
        return PetArtworkAttempt.listByArtwork(artwork).stream()
                .filter(a -> a.valid)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static Map<String, String> previewUrls(int attemptNo) {
        Map<String, String> urls = new LinkedHashMap<>();
        String versionQuery = "?attempt=" + attemptNo;
        urls.put("atlas", "/api/v1/account/pet/artwork/preview/atlas.png" + versionQuery);
        for (SpritePose pose : SpritePose.atlasOrder()) {
            urls.put(
                    pose.name().toLowerCase(),
                    "/api/v1/account/pet/artwork/preview/" + pose.fileName() + versionQuery
            );
        }
        return urls;
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.contains("..") || fileName.contains("/")) {
            throw new ArtworkOperationException("invalid_file", "Nome de arquivo inválido");
        }
        return fileName;
    }
}
