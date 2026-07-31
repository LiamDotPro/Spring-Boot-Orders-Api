package com.liamread.orders.order.dto;

import com.liamread.orders.order.OrderLineEntity;

import java.math.BigDecimal;

public record OrderLineResponse(
        String sku,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static OrderLineResponse from(OrderLineEntity line) {
        return new OrderLineResponse(
                line.getSku(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.lineTotal()
        );
    }
}