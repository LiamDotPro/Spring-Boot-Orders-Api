package com.liamread.orders.order.dto;

import com.liamread.orders.order.OrderEntity;
import com.liamread.orders.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Flat view of an order for list endpoints — deliberately no line items.
 *
 * <p>Leaving the lines out keeps a page of orders to a single query, and means nothing lazy is
 * touched while this is being serialised.
 */
public record OrderListResponse(
        UUID orderId,
        String customerId,
        OrderStatus status,
        BigDecimal total,
        String currency,
        Instant placedAt
) {

    public static OrderListResponse from(OrderEntity order) {
        return new OrderListResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotal(),
                order.getCurrency(),
                order.getPlacedAt()
        );
    }
}
