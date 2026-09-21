package com.ceos24.cgv.domain.store.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MenuStockServiceTest {

    @InjectMocks
    private MenuStockService menuStockService;

    @Test
    @DisplayName("MenuStockService가 성공적으로 초기화된다")
    void contextLoads() {
        assertThat(menuStockService).isNotNull();
    }
}
