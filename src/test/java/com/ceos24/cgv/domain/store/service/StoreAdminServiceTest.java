package com.ceos24.cgv.domain.store.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.store.dto.MenuItem;
import com.ceos24.cgv.domain.store.dto.request.CreateStoreRequest;
import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.store.repository.MenuRepository;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
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
class StoreAdminServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuStockRepository menuStockRepository;

    @Mock
    private TheaterRepository theaterRepository;

    @InjectMocks
    private StoreAdminService storeAdminService;

    @Test
    @DisplayName("스토어와 메뉴 재고를 성공적으로 생성한다")
    void createStore_Success() {
        // given
        Theater theater = mock(Theater.class);
        MenuItem menuItem = new MenuItem(1L, 100L);
        CreateStoreRequest request = new CreateStoreRequest("CGV 매점", List.of(menuItem));

        Menu menu = new Menu("팝콘", 5000L);
        ReflectionTestUtils.setField(menu, "id", 1L);

        given(theaterRepository.findById(1L)).willReturn(Optional.of(theater));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));

        // when
        storeAdminService.createStore(1L, request);

        // then
        verify(storeRepository).save(any(Store.class));
        verify(menuRepository).findById(1L);
        verify(menuStockRepository).saveAll(any());
    }

    @Test
    @DisplayName("존재하지 않는 메뉴로 스토어를 생성하면 예외가 발생한다")
    void createStore_MenuNotFound() {
        // given
        Theater theater = mock(Theater.class);
        MenuItem menuItem = new MenuItem(1L, 100L);
        CreateStoreRequest request = new CreateStoreRequest("CGV 매점", List.of(menuItem));

        given(theaterRepository.findById(1L)).willReturn(Optional.of(theater));
        given(menuRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> storeAdminService.createStore(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MENU_NOT_FOUND);

        verify(storeRepository).save(any(Store.class));
        verify(menuRepository).findById(1L);
    }
}
