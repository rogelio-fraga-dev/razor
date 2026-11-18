package com.razor.api.repository;

import com.razor.api.model.AutomationSend;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationSendRepository extends JpaRepository<AutomationSend, UUID> {

    Optional<AutomationSend> findByBarbershopIdAndKindAndCustomerPhoneAndReferenceKey(
            UUID barbershopId, AutomationSend.Kind kind, String customerPhone, String referenceKey);

    /** Todos os envios de certos tipos (a "memoria" do planejamento de campanhas). */
    List<AutomationSend> findByBarbershopIdAndKindIn(UUID barbershopId, Collection<AutomationSend.Kind> kinds);

    /** Telefones que receberam mensagem automatica de marketing desde {@code since} — limite de frequencia. */
    @Query("""
            select distinct s.customerPhone from AutomationSend s
             where s.barbershopId = :shop
               and s.kind in :kinds
               and s.status = com.razor.api.model.AutomationSend.Status.SENT
               and s.sentAt >= :since
            """)
    List<String> phonesSentSince(@Param("shop") UUID barbershopId,
                                 @Param("kinds") Collection<AutomationSend.Kind> kinds,
                                 @Param("since") LocalDateTime since);

    long countByBarbershopIdAndKindAndStatusAndSentAtGreaterThanEqual(
            UUID barbershopId, AutomationSend.Kind kind, AutomationSend.Status status, LocalDateTime since);
}
