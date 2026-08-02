package com.liamread.orders.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByOrderId(UUID orderId);

    Page<PaymentAttempt> findByCustomerId(String customerId, Pageable pageable);
}
