package com.liamread.orders.order;

import java.util.EnumSet;
import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("Order received, awaiting payment"),
    PAID("Payment confirmed"),
    PROCESSING("Being prepared for dispatch"),
    SHIPPED("Handed to the carrier"),
    DELIVERED("Received by the customer"),
    CANCELLED("Cancelled before dispatch"),
    REFUNDED("Payment returned to the customer"),
    FAILED("Payment or fulfilment could not be completed");

    private static final Set<OrderStatus> TERMINAL =
            EnumSet.of(DELIVERED, CANCELLED, REFUNDED, FAILED);

    private final String description;

    /**
     * True if this is an end state — the order will not change status again.
     */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
