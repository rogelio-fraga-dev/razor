package com.razor.api.repository;

import com.razor.api.model.CustomerTagLink;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerTagLinkRepository extends JpaRepository<CustomerTagLink, CustomerTagLink.Key> {

    /** Vinculos de varios clientes de uma vez — as tags de uma pagina inteira numa consulta so. */
    List<CustomerTagLink> findByCustomerIdIn(Collection<UUID> customerIds);

    List<CustomerTagLink> findByCustomerId(UUID customerId);

    @Query("select l.customerId from CustomerTagLink l where l.tagId = :tagId")
    List<UUID> findCustomerIdsByTagId(@Param("tagId") UUID tagId);

    /** Apaga os vinculos antes da tag — o banco tambem faria (ON DELETE CASCADE), mas o JPA nao sabe. */
    @Modifying
    @Query("delete from CustomerTagLink l where l.tagId = :tagId")
    void deleteByTagId(@Param("tagId") UUID tagId);
}
