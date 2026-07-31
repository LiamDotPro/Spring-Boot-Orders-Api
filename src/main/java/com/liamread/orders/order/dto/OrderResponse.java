package com.liamread.orders.order.dto;

import com.liamread.orders.order.OrderEntity;
import com.liamread.orders.order.OrderStatus;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderResponse(
        @NotNull UUID orderId,
        @NotNull OrderStatus status,
        BigDecimal total,
        Instant placedAt,
        List<OrderLineResponse> items,
        String currency
) {

    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotal(),
                order.getPlacedAt(),
                order.getLines().stream().map(OrderLineResponse::from).toList(),
                order.getCurrency()
        );
    }
}