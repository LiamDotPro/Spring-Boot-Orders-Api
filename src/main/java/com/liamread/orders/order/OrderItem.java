package com.liamread.orders.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItem (
        @NotBlank String sku,
        @NotNull @Min(1) Integer quantity
) {
}
