package com.liamread.orders.payment.dto;

import com.liamread.orders.payment.PaymentAttempt;
import com.liamread.orders.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID paymentId,
        UUID orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerReference,
        String failureReason,
        Instant requestedAt,
        Instant completedAt
) {

    public static PaymentAttemptResponse from(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.getId(),
                attempt.getOrderId(),
                attempt.getCustomerId(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getStatus(),
                attempt.getProviderReference(),
                attempt.getFailureReason(),
                attempt.getRequestedAt(),
                attempt.getCompletedAt()
        );
    }
}
