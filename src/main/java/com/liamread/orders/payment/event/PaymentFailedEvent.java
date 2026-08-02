package com.liamread.orders.payment.event;

import com.liamread.orders.payment.PaymentAttempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code payments.payment-failed.v1}.
 *
 * <p>A declined card is a business outcome, not a processing error. This event is the successful
 * result of handling an {@code OrderPlaced} — the listener that produced it must commit its offset
 * as normal rather than throwing.
 */
public record PaymentFailedEvent(
        String eventId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String reason,
        Instant occurredAt
) {

    public static PaymentFailedEvent from(PaymentAttempt attempt) {
        return new PaymentFailedEvent(
                UUID.randomUUID().toString(),
                attempt.getOrderId().toString(),
                attempt.getCustomerId(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getFailureReason(),
                attempt.getCompletedAt()
        );
    }
}
