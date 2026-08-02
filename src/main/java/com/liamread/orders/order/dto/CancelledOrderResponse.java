package com.liamread.orders.order.dto;

import com.liamread.orders.order.OrderEntity;
import com.liamread.orders.order.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Acknowledgement of a successful cancellation — deliberately not the whole order.
 *
 * <p>The caller already holds the order it asked to cancel; what it does not know is whether the
 * transition was allowed and when it took effect. Returning {@link OrderResponse} here would imply
 * the lines and total are worth re-reading, and would drag the lazy collection into a write
 * transaction for no reason.
 *
 * <p>{@code status} is echoed rather than assumed to be {@code CANCELLED}: it comes from the entity
 * after the transition, so a client that trusts this field stays correct if the state machine ever
 * grows an intermediate step such as {@code CANCELLING}.
 */
public record CancelledOrderResponse(
        UUID orderId,
        OrderStatus status,
        Instant cancelledAt
) {

    public static CancelledOrderResponse from(OrderEntity order) {
        return new CancelledOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getUpdatedAt()
        );
    }
}
