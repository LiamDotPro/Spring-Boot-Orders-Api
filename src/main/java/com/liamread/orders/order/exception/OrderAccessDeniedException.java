package com.liamread.orders.order.exception;

import java.util.UUID;

/**
 * The caller is asking to act on an order that belongs to somebody else.
 *
 * <p>Named for the domain rule rather than for HTTP: the service layer does not know that this
 * becomes a 403, and it must not — that mapping lives in {@code GlobalExceptionHandler}. The name
 * also deliberately avoids Spring Security's own {@code AccessDeniedException}, so that adding the
 * security starter later does not leave two same-named types to disambiguate on every import.
 *
 * <p>The identifiers are carried on the exception for the log line, not for the response body.
 * Telling the caller which customer owns an order confirms the order exists and leaks the owner.
 */
public class OrderAccessDeniedException extends RuntimeException {

    private final UUID orderId;
    private final String customerId;

    public OrderAccessDeniedException(UUID orderId, String customerId) {
        super("Customer " + customerId + " may not act on order " + orderId);
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
