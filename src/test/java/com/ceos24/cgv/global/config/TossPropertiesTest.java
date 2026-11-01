package com.ceos24.cgv.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TossPropertiesTest {

    @Autowired
    private TossProperties tossProperties;

    @Test
    @DisplayName("TossProperties 프로퍼티 바인딩 검증")
    void tossPropertiesBindingTest() {
        assertThat(tossProperties).isNotNull();
        assertThat(tossProperties.secretKey()).isEqualTo("test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6");
        assertThat(tossProperties.baseUrl()).isEqualTo("https://api.tosspayments.com");
    }
}
