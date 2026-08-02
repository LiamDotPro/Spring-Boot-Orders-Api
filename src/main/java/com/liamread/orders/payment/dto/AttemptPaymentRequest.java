package com.liamread.orders.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Body for the manual trigger endpoint.
 *
 * <p>The caller supplies the amount, customer and currency because payments <em>cannot look them
 * up</em> — the orders table belongs to another context. That is not an inconvenience to work
 * around; it is the boundary doing its job, and the Kafka listener you write will be in exactly the
 * same position, taking all three from the event payload.
 */
public record AttemptPaymentRequest(
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency
) {
}
