package com.razor.api.repository;

import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.PaymentMethodFee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodFeeRepository extends JpaRepository<PaymentMethodFee, PaymentMethodFee.Key> {

    List<PaymentMethodFee> findByBarbershopId(UUID barbershopId);

    Optional<PaymentMethodFee> findByBarbershopIdAndPaymentMethod(UUID barbershopId, CashPaymentMethod method);
}
