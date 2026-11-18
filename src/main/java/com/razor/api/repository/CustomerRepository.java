package com.razor.api.repository;

import com.razor.api.model.Customer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // Acesso escopado por tenant (Fase 1). O telefone e unico POR barbearia,
    // entao lookups por telefone/nome sempre passam o barbershop_id.
    Optional<Customer> findByBarbershopIdAndPhoneNumber(UUID barbershopId, String phoneNumber);

    /** Cadastros do telefone em qualquer das grafias dadas (ver {@code PhoneNumbers.variants}). */
    List<Customer> findByBarbershopIdAndPhoneNumberIn(UUID barbershopId, java.util.Collection<String> phoneNumbers);

    Optional<Customer> findByBarbershopIdAndNameIgnoreCase(UUID barbershopId, String name);

    List<Customer> findByBarbershopId(UUID barbershopId);

    long countByBarbershopId(UUID barbershopId);

    /**
     * Busca paginada da tela de Clientes: termo livre (nome ou telefone) + filtros de assinante e
     * de cadastro recente, tudo resolvido <b>no banco</b>.
     *
     * <p>Antes a tela carregava a base inteira e filtrava no navegador. Com paginacao isso deixa
     * de funcionar — a busca so alcancaria a pagina carregada, e o dono concluiria que o cliente
     * nao existe. Por isso filtro e busca desceram junto com a paginacao, e nao depois.
     *
     * <p>{@code term} deve vir ja em minusculas e cercado de {@code %}; nulo desliga o filtro.
     * O {@code lower(c.name) like} nao casa quando o nome e nulo, mas o {@code or} do telefone
     * continua valendo — cliente sem nome ainda e encontrado pelo numero.
     *
     * <p><b>Por que o filtro de data mora numa consulta separada</b> ({@link #searchCreatedSince})
     * em vez de um {@code (:createdSince is null or ...)} aqui: o Postgres recusa a consulta
     * inteira com {@code could not determine data type of parameter} quando um
     * {@code LocalDateTime} nulo aparece num {@code ? is null} — o parametro solto nao tem
     * contexto de tipo para inferir. Com {@code :term} isso nao acontece porque o {@code like}
     * logo adiante ja diz que e texto.
     *
     * <p>As alternativas foram descartadas: {@code cast} deixaria a condicao ilegivel, e trocar
     * o nulo por uma data-limite excluiria silenciosamente quem tem {@code created_at} nulo — a
     * coluna e nullable, entao seria um cliente sumindo da tela sem erro nenhum.
     */
    @Query("""
            select c from Customer c
             where c.barbershopId = :shop
               and (:term is null or lower(c.name) like :term or c.phoneNumber like :term)
               and (:onlySubscribers = false or c.subscriptionPlanId is not null)
            """)
    Page<Customer> search(@Param("shop") UUID barbershopId,
                          @Param("term") String term,
                          @Param("onlySubscribers") boolean onlySubscribers,
                          Pageable pageable);

    /** Mesma busca, restrita a quem foi cadastrado a partir de {@code createdSince}. */
    @Query("""
            select c from Customer c
             where c.barbershopId = :shop
               and (:term is null or lower(c.name) like :term or c.phoneNumber like :term)
               and (:onlySubscribers = false or c.subscriptionPlanId is not null)
               and c.createdAt >= :createdSince
            """)
    Page<Customer> searchCreatedSince(@Param("shop") UUID barbershopId,
                                      @Param("term") String term,
                                      @Param("onlySubscribers") boolean onlySubscribers,
                                      @Param("createdSince") LocalDateTime createdSince,
                                      Pageable pageable);

    /**
     * Mesma busca, restrita a um conjunto de clientes ja recortado por tag e/ou status (V36). O
     * status e calculado em Java (ver {@code CustomerStatusRules}), entao o recorte chega pronto
     * como lista de ids; quem chama garante que ela nao e vazia.
     */
    @Query("""
            select c from Customer c
             where c.barbershopId = :shop
               and c.id in :ids
               and (:term is null or lower(c.name) like :term or c.phoneNumber like :term)
               and (:onlySubscribers = false or c.subscriptionPlanId is not null)
            """)
    Page<Customer> searchAmong(@Param("shop") UUID barbershopId,
                               @Param("ids") java.util.Collection<UUID> ids,
                               @Param("term") String term,
                               @Param("onlySubscribers") boolean onlySubscribers,
                               Pageable pageable);

    /** {@link #searchAmong} restrita a quem foi cadastrado a partir de {@code createdSince}. */
    @Query("""
            select c from Customer c
             where c.barbershopId = :shop
               and c.id in :ids
               and (:term is null or lower(c.name) like :term or c.phoneNumber like :term)
               and (:onlySubscribers = false or c.subscriptionPlanId is not null)
               and c.createdAt >= :createdSince
            """)
    Page<Customer> searchAmongCreatedSince(@Param("shop") UUID barbershopId,
                                           @Param("ids") java.util.Collection<UUID> ids,
                                           @Param("term") String term,
                                           @Param("onlySubscribers") boolean onlySubscribers,
                                           @Param("createdSince") LocalDateTime createdSince,
                                           Pageable pageable);

    /** Quantos assinantes a barbearia tem — o contador do topo da tela, contado no banco. */
    long countByBarbershopIdAndSubscriptionPlanIdIsNotNull(UUID barbershopId);

    /** Cadastrados a partir de uma data — alimenta o filtro "novos (30d)". */
    long countByBarbershopIdAndCreatedAtGreaterThanEqual(UUID barbershopId, LocalDateTime since);
}
