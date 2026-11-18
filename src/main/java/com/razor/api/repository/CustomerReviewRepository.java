package com.razor.api.repository;

import com.razor.api.model.CustomerReview;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerReviewRepository extends JpaRepository<CustomerReview, UUID> {

    /** Pedidos programados que ja venceram (motor de envio, todos os tenants), os mais antigos primeiro. */
    List<CustomerReview> findByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(
            CustomerReview.Status status, LocalDateTime now, Pageable page);

    /** Perguntas enviadas ha mais tempo que o prazo de resposta (viram EXPIRED). */
    List<CustomerReview> findByStatusAndSentAtBefore(CustomerReview.Status status, LocalDateTime limit);

    boolean existsByBarbershopIdAndCustomerPhoneAndStatus(UUID barbershopId, String phone, CustomerReview.Status status);

    /** Pedidos do telefone desde {@code since} que contam para a regra de frequencia. */
    boolean existsByBarbershopIdAndCustomerPhoneAndStatusInAndCreatedAtAfter(
            UUID barbershopId, String phone, Collection<CustomerReview.Status> statuses, LocalDateTime since);

    boolean existsByComandaId(UUID comandaId);

    Optional<CustomerReview> findByIdAndBarbershopId(UUID id, UUID barbershopId);

    /** Notas mais recentes do tenant (card do painel). */
    List<CustomerReview> findByBarbershopIdAndRatingIsNotNullOrderByRatedAtDesc(UUID barbershopId, Pageable page);

    /** Media e quantidade de notas do tenant desde {@code since}: [media, quantidade]. */
    @Query("select avg(r.rating), count(r) from CustomerReview r "
            + "where r.barbershopId = :shop and r.rating is not null and r.ratedAt >= :since")
    List<Object[]> ratingStats(@Param("shop") UUID barbershopId, @Param("since") LocalDateTime since);

    /** Quantidade por nota (1..5) desde {@code since}: [nota, quantidade]. */
    @Query("select r.rating, count(r) from CustomerReview r "
            + "where r.barbershopId = :shop and r.rating is not null and r.ratedAt >= :since group by r.rating")
    List<Object[]> ratingDistribution(@Param("shop") UUID barbershopId, @Param("since") LocalDateTime since);

    /** Pedidos que de fato sairam desde {@code since} (base da taxa de resposta). */
    long countByBarbershopIdAndSentAtAfter(UUID barbershopId, LocalDateTime since);
}
