package com.razor.api.repository;

import com.razor.api.model.ComandaItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComandaItemRepository extends JpaRepository<ComandaItem, UUID> {

    List<ComandaItem> findByComandaIdOrderByCreatedAtAsc(UUID comandaId);

    /** Itens de varias comandas numa consulta so — evita N+1 no relatorio financeiro. */
    List<ComandaItem> findByComandaIdIn(List<UUID> comandaIds);

    Optional<ComandaItem> findByIdAndComandaId(UUID id, UUID comandaId);
}
