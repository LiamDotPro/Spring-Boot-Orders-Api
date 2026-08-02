package com.liamread.orders.payment;

import com.liamread.orders.helpers.CurrencyOptions;
import com.liamread.orders.payment.event.PaymentFailedEvent;
import com.liamread.orders.payment.event.PaymentOutcomePublisher;
import com.liamread.orders.payment.event.PaymentSucceededEvent;
import com.liamread.orders.payment.exception.PaymentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether an order gets paid for, records the attempt, and announces the outcome.
 *
 * <p>Nothing here reads the orders tables, and nothing here imports from
 * {@code com.liamread.orders.order}. Every fact payments needs — who, how much, which currency —
 * arrives in the call. That constraint is the whole reason this context exists rather than being
 * three more methods on {@code OrderService}.
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutcomePublisher outcomePublisher;
    private final PaymentProperties properties;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentOutcomePublisher outcomePublisher,
            PaymentProperties properties
    ) {
        this.paymentRepository = paymentRepository;
        this.outcomePublisher = outcomePublisher;
        this.properties = properties;
    }

    /**
     * Take money for an order, once.
     *
     * <p><strong>Idempotent by natural key.</strong> A second call for the same {@code orderId}
     * returns the existing attempt and charges nothing, so a redelivered {@code OrderPlaced} after
     * a rebalance is harmless. The pre-check below handles the ordinary case; the unique constraint
     * on {@code order_id} handles the case the pre-check cannot — two consumers processing the
     * duplicate at the same instant.
     *
     * <p>In that race the loser's insert throws {@code DataIntegrityViolationException} and this
     * transaction rolls back. That is deliberately not caught: a caller retrying (which is exactly
     * what a Kafka listener does when the offset is not committed) will find the winner's row on
     * the next delivery and return cleanly. Swallowing it here would need a separate transaction
     * for the insert, which is a lot of machinery to avoid one redelivery.
     *
     * @return the attempt, complete — this method never leaves one {@code PENDING}.
     */
    @Transactional
    public PaymentAttempt attemptPayment(UUID orderId, String customerId, BigDecimal amount, String currency) {
        Optional<PaymentAttempt> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("Payment already attempted for order {} — {} (no charge taken)",
                    orderId, existing.get().getStatus());
            return existing.get();
        }

        PaymentAttempt attempt = paymentRepository.save(
                new PaymentAttempt(orderId, customerId, amount, currency));

        simulateProviderLatency();

        Optional<String> decline = declineReason(customerId, amount, currency);

        if (decline.isPresent()) {
            attempt.fail(decline.get());
            log.info("Payment DECLINED for order {} — {}", orderId, decline.get());
            outcomePublisher.paymentFailed(PaymentFailedEvent.from(attempt));
        } else {
            attempt.succeed(newProviderReference());
            log.info("Payment SUCCEEDED for order {} — {} {} ref {}",
                    orderId, amount, currency, attempt.getProviderReference());
            outcomePublisher.paymentSucceeded(PaymentSucceededEvent.from(attempt));
        }

        // No explicit save: `attempt` is managed inside this transaction, so Hibernate's dirty
        // checking flushes the status change at commit.
        return attempt;
    }

    @Transactional(readOnly = true)
    public PaymentAttempt getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    /**
     * The simulated provider. Every rule is deterministic so a failure can be demonstrated on
     * demand — a coin flip would be untestable and undemonstrable.
     *
     * @return the reason to decline, or empty to approve.
     */
    private Optional<String> declineReason(String customerId, BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of("Amount must be greater than zero");
        }

        if (!isSupportedCurrency(currency)) {
            return Optional.of("Unsupported currency: " + currency);
        }

        String suffix = properties.declineCustomerSuffix();
        if (customerId != null && customerId.toUpperCase().endsWith(suffix.toUpperCase())) {
            return Optional.of("Customer flagged for automatic decline (id ends with " + suffix + ")");
        }

        BigDecimal ceiling = properties.autoDeclineAbove();
        if (ceiling != null && amount.compareTo(ceiling) > 0) {
            return Optional.of("Amount " + amount + " exceeds the auto-decline ceiling of " + ceiling);
        }

        return Optional.empty();
    }

    /**
     * Rejecting an unknown currency matters more than it looks: without this, payments would
     * happily "charge" 500 of whatever string the upstream event contained.
     */
    private boolean isSupportedCurrency(String currency) {
        return currency != null
                && Arrays.stream(CurrencyOptions.values())
                .anyMatch(option -> option.name().equalsIgnoreCase(currency));
    }

    private String newProviderReference() {
        return "sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Off by default. Turning it on makes the eventual consistency window visible in the console,
     * but note what you are doing: this sleeps while holding a database connection, and once a
     * Kafka listener is calling this method it also burns the clock against
     * {@code max.poll.interval.ms}. Set it far below that or the consumer gets kicked out of the
     * group mid-payment and you have engineered yourself a rebalance loop.
     */
    private void simulateProviderLatency() {
        long millis = properties.providerLatencyMillis();
        if (millis <= 0) return;

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
