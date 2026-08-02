package com.liamread.orders.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payments' own reading of the {@code orders.order-placed.v1} payload.
 *
 * <p>This is <strong>not</strong> {@code com.liamread.orders.order.event.OrderPlacedEvent} and must
 * never be replaced by an import of it. The two classes are deliberately unrelated: the contract
 * between the contexts is the JSON on the wire, and this record is one consumer's interpretation of
 * it. Across two repositories you would have no choice; doing it inside one is what keeps the
 * boundary honest.
 *
 * <p>Consequences worth noticing. Payments can ignore fields it does not care about, and orders can
 * add a field without recompiling payments. The cost is that the producer stamps its own
 * fully-qualified class name into the {@code __TypeId__} header, which will not resolve here — see
 * the deserialization notes in ORD-014 before wiring the listener up.
 *
 * <p>{@code orderId} is a {@code String} because that is what the producer serialises. Parsing it
 * to a {@code UUID} is the consumer's job, and it can fail — which is a poison message, not a
 * retryable error.
 */
public record OrderPlacedEvent(
        String eventId,
        String orderId,
        String customerId,
        BigDecimal total,
        String currency,
        Instant occurredAt
) {

    /** @throws IllegalArgumentException if the producer sent something that is not a UUID. */
    public UUID orderUuid() {
        return UUID.fromString(orderId);
    }
}
