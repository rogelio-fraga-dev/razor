package com.razor.api.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.SiteImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@DisplayName("Fotos no S3 (V50): chave por barbearia, bucket privado, sem AWS de verdade")
class S3ImageStorageTest {

    /** S3 falso em memoria: implementa so o que o storage usa. */
    static final class FakeS3 implements S3Client {
        final Map<String, byte[]> objects = new HashMap<>();
        final Map<String, String> contentTypes = new HashMap<>();
        boolean failDelete;

        private static String id(String bucket, String key) {
            return bucket + "|" + key;
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest req, RequestBody body) {
            try (InputStream in = body.contentStreamProvider().newStream()) {
                objects.put(id(req.bucket(), req.key()), in.readAllBytes());
                contentTypes.put(id(req.bucket(), req.key()), req.contentType());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return PutObjectResponse.builder().build();
        }

        @Override
        public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest req) {
            byte[] b = objects.get(id(req.bucket(), req.key()));
            if (b == null) throw NoSuchKeyException.builder().message("nope").build();
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), b);
        }

        @Override
        public DeleteObjectResponse deleteObject(DeleteObjectRequest req) {
            if (failDelete) throw new IllegalStateException("rede");
            objects.remove(id(req.bucket(), req.key()));
            return DeleteObjectResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {
        }
    }

    private final FakeS3 s3 = new FakeS3();
    private final S3ImageStorage storage = new S3ImageStorage(s3, "fotos", "site-images/");
    private final UUID shop = UUID.randomUUID();

    private SiteImage image() {
        return SiteImage.builder().id(UUID.randomUUID()).barbershopId(shop).contentType("image/webp").sizeBytes(3).build();
    }

    @Test
    @DisplayName("put sobe com a chave <prefixo><barbearia>/<foto> e o tipo detectado; o registro fica sem bytes")
    void putUploadsWithTenantKey() {
        SiteImage img = image();
        img.setBytes(new byte[] {7});
        storage.put(img, new byte[] {1, 2, 3});

        String key = "fotos|site-images/" + shop + "/" + img.getId();
        assertThat(s3.objects.get(key)).containsExactly(1, 2, 3);
        assertThat(s3.contentTypes.get(key)).isEqualTo("image/webp");
        assertThat(img.getBytes()).isNull();
        assertThat(storage.backend()).isEqualTo("S3");
    }

    @Test
    @DisplayName("get devolve os bytes; objeto inexistente = vazio")
    void getRoundTripAndMissing() {
        SiteImage img = image();
        storage.put(img, new byte[] {4, 5});
        assertThat(storage.get(img)).hasValueSatisfying(b -> assertThat(b).containsExactly(4, 5));
        assertThat(storage.get(image())).isEmpty();
    }

    @Test
    @DisplayName("delete remove o objeto; falha de rede só é logada (registro já foi apagado)")
    void deleteIsBestEffort() {
        SiteImage img = image();
        storage.put(img, new byte[] {1});
        storage.delete(shop, img.getId());
        assertThat(s3.objects).isEmpty();

        s3.failDelete = true;
        assertThatCode(() -> storage.delete(shop, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("registro: ativo pela config; leitura pelo backend gravado na foto")
    void registryPicksByConfigAndByRow() {
        RazorProperties props = new RazorProperties();
        props.getStorage().setType("s3");
        DbImageStorage db = new DbImageStorage();
        ImageStorageRegistry registry = new ImageStorageRegistry(List.of(db, storage), props);

        assertThat(registry.active()).isSameAs(storage);
        assertThat(registry.forBackend("DB")).isSameAs(db);
        assertThat(registry.forBackend(null)).isSameAs(db);

        props.getStorage().setType("db");
        ImageStorageRegistry onlyDb = new ImageStorageRegistry(List.of(db), props);
        assertThat(onlyDb.active()).isSameAs(db);
        // Foto no S3 com o S3 desligado: erro claro, nao NPE.
        assertThatThrownBy(() -> onlyDb.forBackend("S3")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("S3 ligado sem bucket falha no boot")
    void s3WithoutBucketFails() {
        RazorProperties props = new RazorProperties();
        props.getStorage().setType("s3");
        assertThatThrownBy(() -> new S3StorageConfig().razorS3Client(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucket");
    }
}
