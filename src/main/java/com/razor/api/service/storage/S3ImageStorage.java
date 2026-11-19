package com.razor.api.service.storage;

import com.razor.api.model.SiteImage;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Fotos num bucket S3 compativel (AWS S3, MinIO, Cloudflare R2...), V50. Ligado so por
 * {@code razor.storage.type=s3} (ver {@link S3StorageConfig}).
 *
 * <p>Chave: {@code <prefixo><barbearia>/<foto>} — da para apagar/medir tudo de uma casa pelo
 * prefixo. O bucket fica PRIVADO: quem serve a foto e o backend em {@code /api/site/img/{uuid}}
 * (streaming), entao nao ha URL assinada vencendo dentro de um cache de um ano nem CORS.
 */
@Slf4j
public class S3ImageStorage implements ImageStorage {

    public static final String BACKEND = "S3";

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3ImageStorage(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public String backend() {
        return BACKEND;
    }

    String key(UUID barbershopId, UUID imageId) {
        return prefix + barbershopId + "/" + imageId;
    }

    @Override
    public void put(SiteImage image, byte[] bytes) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key(image.getBarbershopId(), image.getId()))
                        .contentType(image.getContentType())
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes));
        // O registro nao leva os bytes: eles moram no bucket.
        image.setBytes(null);
    }

    @Override
    public Optional<byte[]> get(SiteImage image) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key(image.getBarbershopId(), image.getId()))
                    .build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID barbershopId, UUID imageId) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key(barbershopId, imageId)).build());
        } catch (Exception e) {
            // O registro ja foi apagado: sobra um objeto orfao no bucket, inofensivo (privado).
            log.warn("[SITE] Nao consegui apagar a foto {} do S3: {}", imageId, e.getClass().getSimpleName());
        }
    }
}
