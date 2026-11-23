package com.razor.api.service.storage;

import com.razor.api.model.SiteImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Padrao: a foto mora no proprio Postgres, na coluna {@code site_image.bytes} (V45). Sempre
 * registrado — mesmo com o S3 ligado, as fotos antigas (gravadas como DB) continuam lidas daqui.
 */
@Component
public class DbImageStorage implements ImageStorage {

    public static final String BACKEND = "DB";

    @Override
    public String backend() {
        return BACKEND;
    }

    @Override
    public void put(SiteImage image, byte[] bytes) {
        image.setBytes(bytes);
    }

    @Override
    public Optional<byte[]> get(SiteImage image) {
        return Optional.ofNullable(image.getBytes());
    }

    @Override
    public void delete(UUID barbershopId, UUID imageId) {
        // Os bytes vao embora junto com o registro.
    }
}
