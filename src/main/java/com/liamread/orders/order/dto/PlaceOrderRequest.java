package com.liamread.orders.order.dto;


import com.liamread.orders.order.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record PlaceOrderRequest(
        @NotBlank String customerId,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotEmpty List<@Valid OrderItem> items
) {
}
