package com.liamread.orders.payment.event;

import lombok.extern.slf4j.Slf4j;

/**
 * The stand-in used until a Kafka implementation exists, so the payments context is runnable and
 * testable on its own.
 *
 * <p>It logs loudly rather than silently doing nothing: seeing "NOT PUBLISHED" in the console is
 * the reminder that the order will stay {@code PENDING}, because nothing is carrying the outcome
 * back to the orders context yet.
 */
@Slf4j
public class LoggingPaymentOutcomePublisher implements PaymentOutcomePublisher {

    @Override
    public void paymentSucceeded(PaymentSucceededEvent event) {
        log.warn("PaymentSucceeded NOT PUBLISHED (no Kafka publisher registered) — order {} ref {}",
                event.orderId(), event.providerReference());
    }

    @Override
    public void paymentFailed(PaymentFailedEvent event) {
        log.warn("PaymentFailed NOT PUBLISHED (no Kafka publisher registered) — order {} reason {}",
                event.orderId(), event.reason());
    }
}
