package com.liamread.orders.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Bound from the {@code payments.*} block in {@code application.yml}.
 *
 * <p>Both rules exist so failures are reproducible. A simulator that declines at random cannot be
 * tested and cannot be demonstrated — you need to be able to say "this order will fail" before you
 * place it.
 *
 * @param autoDeclineAbove    orders strictly greater than this are declined. Null disables the rule.
 * @param declineCustomerSuffix a customer id ending with this is always declined, whatever the
 *                              amount. Lets you force a failure from the UI without editing config.
 * @param providerLatency     milliseconds to sleep before answering, so the asynchronous gap is
 *                            visible in the console rather than instant.
 */
@ConfigurationProperties("payments")
public record PaymentProperties(
        BigDecimal autoDeclineAbove,
        String declineCustomerSuffix,
        Long providerLatencyMillis
) {

    public PaymentProperties {
        if (declineCustomerSuffix == null || declineCustomerSuffix.isBlank()) {
            declineCustomerSuffix = "-DECLINE";
        }
        if (providerLatencyMillis == null || providerLatencyMillis < 0) {
            providerLatencyMillis = 0L;
        }
    }
}
