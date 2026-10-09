package com.example.quanlymuahang.sharedkernel.domain;

import com.example.quanlymuahang.domain.common.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable value object shared by procurement and pricing contexts. */
public record Money(BigDecimal amount, CurrencyCode currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > 4) {
            throw new IllegalArgumentException("Money supports at most four decimal places");
        }
    }
}
