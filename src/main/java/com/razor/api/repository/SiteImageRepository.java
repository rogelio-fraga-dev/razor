package com.razor.api.repository;

import com.razor.api.model.SiteImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Fotos do site (V45). Tudo do painel filtra por {@code barbershopId} explicitamente — o id da
 * foto e publico (vai na URL), entao "achar pelo id" sozinho deixaria um dono apagar a foto de
 * outra barbearia. So a leitura publica ({@code findById}) dispensa o tenant.
 */
@Repository
public interface SiteImageRepository extends JpaRepository<SiteImage, UUID> {

    /** Metadados sem os bytes: listar 40 fotos nao pode carregar 80MB na memoria. */
    interface Info {
        UUID getId();

        String getContentType();

        Integer getSizeBytes();

        LocalDateTime getCreatedAt();
    }

    @Query("select i.id as id, i.contentType as contentType, i.sizeBytes as sizeBytes, i.createdAt as createdAt "
            + "from SiteImage i where i.barbershopId = :shop order by i.createdAt desc")
    List<Info> listInfo(@Param("shop") UUID barbershopId);

    long countByBarbershopId(UUID barbershopId);

    boolean existsByIdAndBarbershopId(UUID id, UUID barbershopId);

    @Modifying
    @Query("delete from SiteImage i where i.id = :id and i.barbershopId = :shop")
    int deleteByIdAndShop(@Param("id") UUID id, @Param("shop") UUID barbershopId);
}
