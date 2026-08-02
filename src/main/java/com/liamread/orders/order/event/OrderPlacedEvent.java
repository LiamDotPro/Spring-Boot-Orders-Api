package com.liamread.orders.order.event;

import com.liamread.orders.order.OrderEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPlacedEvent(
        String eventId,
        String orderId,
        String customerId,
        BigDecimal total,
        String currency,
        Instant occurredAt
) {
    public static OrderPlacedEvent from(OrderEntity order) {
        // build and return one
        return new OrderPlacedEvent(UUID.randomUUID().toString(), order.getId().toString(), order.getCustomerId(), order.getTotal(), order.getCurrency(), order.getPlacedAt());
    }
}