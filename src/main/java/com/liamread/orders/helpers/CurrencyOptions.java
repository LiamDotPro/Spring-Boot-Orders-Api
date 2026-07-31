package com.liamread.orders.helpers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CurrencyOptions {
    GBP("Great British Pounds"),
    USD("United States Dollars"),
    EUR("European Union Currency");

    private final String description;

}
