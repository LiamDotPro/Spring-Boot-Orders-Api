package com.liamread.orders.order.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CancelOrderRequest (
        @NotBlank String customerId,
        @NotBlank UUID orderId
) {
}
