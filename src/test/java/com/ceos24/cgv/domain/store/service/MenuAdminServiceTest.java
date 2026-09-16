package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.store.domain.Menu;
import com.ceos24.cgv.domain.store.dto.request.CreateMenuRequest;
import com.ceos24.cgv.domain.store.repository.MenuRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MenuAdminServiceTest {

    @Mock
    private MenuRepository menuRepository;

    @InjectMocks
    private MenuAdminService menuAdminService;

    @Test
    @DisplayName("메뉴를 성공적으로 생성한다")
    void createMenu_Success() {
        // given
        CreateMenuRequest request = new CreateMenuRequest("팝콘", 5000L);

        // when
        menuAdminService.createMenu(request);

        // then
        verify(menuRepository).save(any(Menu.class));
    }
}
