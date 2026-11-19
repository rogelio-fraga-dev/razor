package com.razor.api.service.storage;

import com.razor.api.model.SiteImage;
import java.util.Optional;
import java.util.UUID;

/**
 * Onde moram os BYTES das fotos do site (V50). O registro da foto (dono, tipo, tamanho, data)
 * fica sempre em {@code site_image}; so o arquivo muda de lugar.
 *
 * <p>Implementacoes: {@link DbImageStorage} (padrao — no proprio Postgres, como na V45) e
 * {@link S3ImageStorage} (bucket S3 compativel, ligado por {@code razor.storage.type=s3}).
 *
 * <p>A URL publica nao depende disto: {@code /api/site/img/{uuid}} e sempre servida pelo backend,
 * que le os bytes daqui e responde com os mesmos cabecalhos (nosniff, cache imutavel).
 */
public interface ImageStorage {

    /** Nome gravado em {@code site_image.storage_backend}: {@code DB} ou {@code S3}. */
    String backend();

    /**
     * Guarda os bytes de uma foto nova, ANTES do registro ser salvo. O banco poe os bytes no
     * proprio registro; o S3 sobe o objeto e deixa o registro sem bytes.
     */
    void put(SiteImage image, byte[] bytes);

    /** Os bytes da foto; vazio se o arquivo nao existe mais. */
    Optional<byte[]> get(SiteImage image);

    /** Apaga o arquivo (o registro e apagado por quem chama). Falha so e logada. */
    void delete(UUID barbershopId, UUID imageId);
}
