package com.razor.api.repository;

import com.razor.api.model.Interaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    boolean existsByMessageId(String messageId);

    @Transactional
    void deleteByCustomerId(java.util.UUID customerId);
    
    List<Interaction> findByCustomerIdOrderByTimestampDesc(UUID customerId);
    
    @Query("SELECT i FROM Interaction i WHERE i.customer.id = :customerId ORDER BY i.timestamp DESC")
    List<Interaction> findRecentInteractionsByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);
    
    /** Horarios das mensagens de um tipo no intervalo [from, to) — so a coluna, sem o texto. */
    @Query("""
            SELECT i.timestamp FROM Interaction i
             WHERE i.barbershopId = :shop AND i.type = :type
               AND i.timestamp >= :from AND i.timestamp < :to
            """)
    List<java.time.LocalDateTime> findTimestamps(@Param("shop") UUID barbershopId,
                                                 @Param("type") Interaction.InteractionType type,
                                                 @Param("from") java.time.LocalDateTime from,
                                                 @Param("to") java.time.LocalDateTime to);

    /** Horario da ultima mensagem de um tipo na barbearia (teste do bot no assistente, V49). */
    @Query("SELECT MAX(i.timestamp) FROM Interaction i WHERE i.barbershopId = :shop AND i.type = :type")
    java.time.LocalDateTime findLastTimestamp(@Param("shop") UUID barbershopId,
                                              @Param("type") Interaction.InteractionType type);

    default List<Interaction> findRecentInteractionsByCustomerId(UUID customerId) {
        return findRecentInteractionsByCustomerId(customerId, Pageable.ofSize(10));
    }
}
