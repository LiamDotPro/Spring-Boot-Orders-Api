package com.liamread.orders.payment.exception;

import java.util.UUID;

/**
 * No payment has been attempted for this order.
 *
 * <p>Note this is genuinely a 404 rather than a 204 or an empty body: the resource
 * {@code /api/payments/{orderId}} does not exist yet. It may exist a moment later, once the
 * {@code OrderPlaced} event has been consumed — which is a normal state for an eventually
 * consistent system to be in, not an error worth alarming about.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID orderId) {
        super("No payment attempt for order: " + orderId);
    }
}
