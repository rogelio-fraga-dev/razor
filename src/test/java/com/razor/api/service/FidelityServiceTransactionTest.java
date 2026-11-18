package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prova (com o proxy transacional de verdade do Spring) que o ponto de fidelidade dado no
 * {@code afterCommit} do fechamento da comanda roda numa transacao NOVA.
 *
 * <p>O gerenciador de transacao aqui e um dublê que imita o que importa do JPA: os recursos da
 * transacao continuam presos a thread ate o fim do {@code afterCompletion}. Com
 * {@code REQUIRED}, a chamada feita no {@code afterCommit} "participava" da transacao ja
 * confirmada — e o save do ponto nunca era commitado.
 */
class FidelityServiceTransactionTest {

    private static final String PHONE = "5511999990000";

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Conta quantas transacoes fisicas foram abertas e quantos commits aconteceram. */
    static class RecordingTxManager extends AbstractPlatformTransactionManager {
        final List<String> events = new ArrayList<>();
        private final ThreadLocal<Object> bound = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            return new Object[]{bound.get()};
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((Object[]) transaction)[0] != null;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            Object tx = new Object();
            ((Object[]) transaction)[0] = tx;
            bound.set(tx);
            events.add("begin");
        }

        @Override
        protected Object doSuspend(Object transaction) {
            Object suspended = bound.get();
            bound.remove();
            ((Object[]) transaction)[0] = null;
            return suspended;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            bound.set(suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            // Como o JpaTransactionManager: so solta o recurso DEPOIS do afterCommit/afterCompletion.
            bound.remove();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        RecordingTxManager transactionManager() {
            return new RecordingTxManager();
        }

        @Bean
        CustomerRepository customerRepository() {
            return mock(CustomerRepository.class);
        }

        @Bean
        CouponService couponService() {
            return mock(CouponService.class);
        }

        @Bean
        FidelityService fidelityService(CustomerRepository repo, CouponService coupons) {
            return new FidelityService(repo, coupons);
        }
    }

    @Test
    @DisplayName("registerVisit chamado no afterCommit abre e commita uma transacao propria")
    void registerVisit_inAfterCommit_runsInNewTransaction() {
        TenantContext.set(TenantContext.DEFAULT_BARBERSHOP_ID);
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            RecordingTxManager txm = ctx.getBean(RecordingTxManager.class);
            CustomerRepository repo = ctx.getBean(CustomerRepository.class);
            FidelityService service = ctx.getBean(FidelityService.class);

            Customer c = Customer.builder().id(UUID.randomUUID())
                    .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                    .phoneNumber(PHONE).fidelityPoints(2).build();
            when(repo.findByBarbershopIdAndPhoneNumber(any(UUID.class), eq(PHONE))).thenReturn(Optional.of(c));
            List<Boolean> txAtivaNoSave = new ArrayList<>();
            when(repo.save(any(Customer.class))).thenAnswer(inv -> {
                txAtivaNoSave.add(TransactionSynchronizationManager.isActualTransactionActive());
                txm.events.add("save");
                return inv.getArgument(0);
            });

            // Simula o fechamento da comanda: transacao externa que registra o afterCommit.
            new TransactionTemplate((PlatformTransactionManager) txm).executeWithoutResult(status ->
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            service.registerVisit(PHONE);
                        }
                    }));

            // begin(fechamento) commit(fechamento) begin(fidelidade) save commit(fidelidade)
            assertThat(txm.events).containsExactly("begin", "commit", "begin", "save", "commit");
            assertThat(txAtivaNoSave).containsExactly(true);
            assertThat(c.getFidelityPoints()).isEqualTo(3);
        }
    }
}
