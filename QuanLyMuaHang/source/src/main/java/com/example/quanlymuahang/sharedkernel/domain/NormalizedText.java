package com.example.quanlymuahang.sharedkernel.domain;

import java.util.Objects;

public record NormalizedText(String value) {
    public NormalizedText {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Text must not be blank");
        }
    }
}
