package com.liamread.orders.order.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CatalogueItem(
        @NotBlank String sku,
        @NotBlank String description,
        @NotBlank @Min(1) Integer quantity,
        @NotBlank BigDecimal unitPrice
) {
}
