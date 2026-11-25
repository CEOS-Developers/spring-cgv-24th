package com.ceos24.cgv.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskerTest {

    @Test
    void null은_문자열_null로_표시한다() {
        assertThat(LogMasker.mask(null)).isEqualTo("null");
    }

    @Test
    void 짧은_값은_전부_가린다() {
        assertThat(LogMasker.mask("abc")).isEqualTo("****");
        assertThat(LogMasker.mask("1234567")).isEqualTo("****");
    }

    @Test
    void 긴_값은_뒤_4자리만_남긴다() {
        assertThat(LogMasker.mask("tgen_20261114abcd1234")).isEqualTo("****1234");
        assertThat(LogMasker.mask("12345678")).isEqualTo("****5678");
    }
}
