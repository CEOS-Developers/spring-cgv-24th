package com.ceos24.cgv.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.store.dto.response.MenuStockInfo;
import com.ceos24.cgv.domain.store.dto.response.StoreResponse;
import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.domain.store.entity.MenuStock;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MenuStockRepository menuStockRepository;

    @InjectMocks
    private StoreService storeService;

    @Test
    @DisplayName("스토어 정보를 성공적으로 조회한다")
    void getStoreInfo_Success() {
        // given
        Long theaterId = 1L;
        Long storeId = 1L;

        Theater theater = mock(Theater.class);
        Store store = new Store(theater, "CGV 매점");
        ReflectionTestUtils.setField(store, "id", storeId);

        Menu menu = new Menu("팝콘", 5000L);
        ReflectionTestUtils.setField(menu, "id", 1L);

        MenuStock menuStock = new MenuStock(menu, store, 100L);

        given(storeRepository.findByTheaterId(theaterId)).willReturn(Optional.of(store));
        given(menuStockRepository.findByStoreIdWithMenu(storeId)).willReturn(List.of(menuStock));

        // when
        StoreResponse response = storeService.getStoreInfo(theaterId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.storeName()).isEqualTo("CGV 매점");
        assertThat(response.menuStocks()).hasSize(1);

        MenuStockInfo menuStockInfo = response.menuStocks().get(0);
        assertThat(menuStockInfo.menuId()).isEqualTo(1L);
        assertThat(menuStockInfo.name()).isEqualTo("팝콘");
        assertThat(menuStockInfo.price()).isEqualTo(5000L);
        assertThat(menuStockInfo.stock()).isEqualTo(100L);

        verify(storeRepository).findByTheaterId(theaterId);
        verify(menuStockRepository).findByStoreIdWithMenu(storeId);
    }

    @Test
    @DisplayName("존재하지 않는 극장의 스토어를 조회하면 예외가 발생한다")
    void getStoreInfo_StoreNotFound() {
        // given
        Long theaterId = 1L;
        given(storeRepository.findByTheaterId(theaterId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeService.getStoreInfo(theaterId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORE_NOT_FOUND);

        verify(storeRepository).findByTheaterId(theaterId);
    }
}
