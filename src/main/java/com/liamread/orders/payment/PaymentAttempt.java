package com.liamread.orders.payment;

import com.liamread.orders.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to take money for one order.
 *
 * <p>This table belongs to the payments context and nothing outside it may read or write it.
 * Note what is <em>not</em> here: no reference to {@code OrderEntity}, no foreign key, no JPA
 * relationship. {@link #orderId} is a plain {@code UUID} — a correlation id for an aggregate that
 * lives somewhere else and that payments deliberately cannot navigate to.
 *
 * <p>The unique constraint on {@code order_id} is load-bearing. It is what makes a redelivered
 * {@code OrderPlaced} event unable to charge a customer twice, and unlike a read-then-check it
 * holds even when two consumers process the duplicate concurrently.
 */
@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_attempt_order", columnNames = "order_id")
)
@Getter
public class PaymentAttempt extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private String customerId;

    /** Matches {@code OrderEntity.total}'s precision so no rounding happens crossing the boundary. */
    @Column(precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** The simulated provider's id for the charge. Null until the attempt succeeds. */
    private String providerReference;

    /** Null unless {@link #status} is {@link PaymentStatus#FAILED}. */
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    private Instant completedAt;

    protected PaymentAttempt() { }   // for Hibernate only

    PaymentAttempt(UUID orderId, String customerId, BigDecimal amount, String currency) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    void succeed(String providerReference) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.completedAt = Instant.now();
    }

    void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public boolean isComplete() {
        return status != PaymentStatus.PENDING;
    }
}
