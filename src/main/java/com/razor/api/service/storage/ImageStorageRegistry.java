package com.razor.api.service.storage;

import com.razor.api.config.RazorProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Escolhe o {@link ImageStorage}: o ATIVO (config {@code razor.storage.type}) recebe as fotos
 * novas; a leitura e a remocao usam o backend gravado em cada foto. Assim ligar o S3 nao quebra
 * as fotos que ja estavam no banco, e voltar para o banco nao quebra as que foram para o S3
 * (enquanto o S3 continuar configurado).
 */
@Component
public class ImageStorageRegistry {

    private final Map<String, ImageStorage> byBackend;
    private final ImageStorage active;

    public ImageStorageRegistry(List<ImageStorage> storages, RazorProperties properties) {
        this.byBackend = storages.stream().collect(Collectors.toMap(
                s -> s.backend().toUpperCase(Locale.ROOT), Function.identity()));
        String type = properties.getStorage().getType();
        String wanted = (type == null || type.isBlank() ? DbImageStorage.BACKEND : type).trim().toUpperCase(Locale.ROOT);
        this.active = byBackend.get(wanted);
        if (active == null) {
            throw new IllegalStateException("razor.storage.type='" + type + "' desconhecido (use db ou s3).");
        }
    }

    /** Onde vao as fotos novas. */
    public ImageStorage active() {
        return active;
    }

    /**
     * Storage de uma foto ja gravada.
     *
     * @throws IllegalStateException se a foto esta num backend que nao esta configurado agora
     *         (ex.: fotos no S3 e o S3 foi desligado)
     */
    public ImageStorage forBackend(String backend) {
        String key = backend == null ? DbImageStorage.BACKEND : backend.toUpperCase(Locale.ROOT);
        ImageStorage s = byBackend.get(key);
        if (s == null) {
            throw new IllegalStateException("Foto gravada no backend " + key + ", que não está configurado.");
        }
        return s;
    }
}
