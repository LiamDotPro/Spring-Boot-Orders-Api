package com.liamread.orders.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Note {@code @NotNull} and {@code @Min}, not {@code @NotBlank}. {@code @NotBlank} is string-only —
 * putting it on a number compiles fine and then throws at validation time, which is the bug the old
 * {@code CatalogueItem} shipped with.
 */
public record RestockRequest(
        @NotNull @Min(1) Integer quantity
) {
}
