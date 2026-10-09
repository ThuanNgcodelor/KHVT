package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.importing.application.LocalizedNumberParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedNumberParserTest {
    @Test void parsesVietnameseDecimalAndThousandsSeparators() {
        assertThat(LocalizedNumberParser.parse("690.000")).isEqualByComparingTo("690000");
        assertThat(LocalizedNumberParser.parse("1,5")).isEqualByComparingTo("1.5");
        assertThat(LocalizedNumberParser.parse("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(LocalizedNumberParser.parse("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(LocalizedNumberParser.parse("chưa biết")).isNull();
    }
}
