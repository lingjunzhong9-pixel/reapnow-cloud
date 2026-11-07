package com.reapnow.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderIdAndTypeAndStatus(Long orderId, PaymentType type, PaymentStatus status);

    List<Payment> findByOrderId(Long orderId);
}
