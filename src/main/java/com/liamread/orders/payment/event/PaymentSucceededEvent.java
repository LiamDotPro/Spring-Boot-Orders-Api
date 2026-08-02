package com.liamread.orders.payment.event;

import com.liamread.orders.payment.PaymentAttempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code payments.payment-succeeded.v1}.
 *
 * <p>{@code eventId} identifies this delivery of this fact and is distinct from {@code orderId} —
 * it is what a consumer deduplicates on (ORD-012).
 */
public record PaymentSucceededEvent(
        String eventId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String providerReference,
        Instant occurredAt
) {

    public static PaymentSucceededEvent from(PaymentAttempt attempt) {
        return new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                attempt.getOrderId().toString(),
                attempt.getCustomerId(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getProviderReference(),
                attempt.getCompletedAt()
        );
    }
}
