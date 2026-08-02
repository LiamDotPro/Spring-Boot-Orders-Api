package com.liamread.orders.order.exception;

import com.liamread.orders.order.OrderStatus;

/**
 * A status change the state machine does not allow — cancelling a delivered order, for instance.
 *
 * <p>Carries both ends of the attempted move rather than a pre-built sentence, so the handler can
 * put them in the response as separate fields and callers can branch on them without parsing
 * prose. The message exists for logs and stack traces.
 *
 * <p>Distinct from {@link OrderNotFoundException} and {@link OrderAccessDeniedException} because it
 * means something different to the caller: the order exists and is theirs, but its current state
 * forbids what they asked for. That is a 409, and retrying will not help until the state changes.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot move order from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
