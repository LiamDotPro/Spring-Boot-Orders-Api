package com.liamread.orders.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("Attempt created, provider has not answered"),
    SUCCEEDED("Provider accepted the charge"),
    FAILED("Provider declined the charge");

    private final String description;
}
