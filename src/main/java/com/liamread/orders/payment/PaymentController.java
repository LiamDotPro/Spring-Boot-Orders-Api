package com.liamread.orders.payment;

import com.liamread.orders.payment.dto.AttemptPaymentRequest;
import com.liamread.orders.payment.dto.PaymentAttemptResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentAttemptResponse> getPayment(@PathVariable UUID orderId) {
        return ResponseEntity.ok(PaymentAttemptResponse.from(paymentService.getByOrderId(orderId)));
    }

    /**
     * Trigger a payment by hand.
     *
     * <p>A development affordance, not part of the domain: in the finished design the only thing
     * that starts a payment is an {@code OrderPlaced} event. It exists so the payments context can
     * be exercised — and the dev console's "simulate payment" button can work — before the Kafka
     * listener is written, and so you can drive the same code path without a broker when testing.
     *
     * <p>It is idempotent for the same reason the listener will be: a second call returns the
     * existing attempt rather than charging again. Once your listener is in place, expect this to
     * usually return the attempt the listener already created.
     */
    @PostMapping("/{orderId}/attempt")
    public ResponseEntity<PaymentAttemptResponse> attemptPayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody AttemptPaymentRequest request
    ) {
        PaymentAttempt attempt = paymentService.attemptPayment(
                orderId, request.customerId(), request.amount(), request.currency());

        return ResponseEntity.ok(PaymentAttemptResponse.from(attempt));
    }
}
