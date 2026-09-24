package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    Optional<Payment> findByPaymentReference(String paymentReference);

    boolean existsByPaymentReference(String paymentReference);
}
