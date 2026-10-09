package com.example.quanlymuahang.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void removesVietnameseDiacriticsAndNormalizesWhitespace() {
        assertThat(TextNormalizer.normalize("  CÔNG   ty   ĐẶC BIỆT  "))
                .isEqualTo("cong ty dac biet");
    }

    @Test
    void handlesNull() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
