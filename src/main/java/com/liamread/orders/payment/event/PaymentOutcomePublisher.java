package com.liamread.orders.payment.event;

/**
 * How the payments context announces what it decided.
 *
 * <p><strong>This interface is the seam you are meant to fill in.</strong> {@code PaymentService}
 * depends on it and knows nothing about Kafka, topics or serialization. Write a
 * {@code KafkaPaymentOutcomePublisher} that implements it with a {@code KafkaTemplate}, annotate it
 * {@code @Component}, and it replaces the logging default automatically — no other file changes.
 *
 * <p>Implementations are called from inside {@code PaymentService}'s transaction. That is the
 * dual-write problem in ORD-012, left in place on purpose: if the process dies between the commit
 * and the send, the attempt exists and nobody was told.
 */
public interface PaymentOutcomePublisher {

    void paymentSucceeded(PaymentSucceededEvent event);

    void paymentFailed(PaymentFailedEvent event);
}
