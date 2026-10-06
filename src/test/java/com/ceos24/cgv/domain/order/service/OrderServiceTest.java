package com.ceos24.cgv.domain.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.order.dto.request.CreateOrderRequest;
import com.ceos24.cgv.domain.order.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.repository.OrderItemRepository;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.domain.store.entity.MenuStock;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
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
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MenuStockRepository menuStockRepository;

    @Test
    @DisplayName("주문 생성 성공")
    void createOrder_Success() {
        // given
        Long memberId = 1L;
        Long storeId = 1L;
        Long menuId = 1L;
        Long quantity = 2L;

        OrderItemRequest itemRequest = new OrderItemRequest(menuId, quantity);
        CreateOrderRequest request = new CreateOrderRequest(storeId, List.of(itemRequest));

        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(memberId);

        Store store = new Store(null, "Test Store");
        ReflectionTestUtils.setField(store, "id", storeId);

        Menu menu = new Menu("Popcorn", 5000L);
        ReflectionTestUtils.setField(menu, "id", menuId);

        MenuStock menuStock = new MenuStock(menu, store, 10L);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
        given(menuStockRepository.findAllByStoreIdAndMenuIdIn(storeId, List.of(menuId)))
                .willReturn(List.of(menuStock));

        // when
        orderService.createOrder(memberId, request);

        // then
        assertEquals(8L, menuStock.getStock());
        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("존재하지 않는 회원일 경우 예외 발생")
    void createOrder_MemberNotFound() {
        // given
        Long memberId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(1L, List.of());

        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when & then
        BusinessException exception =
                assertThrows(BusinessException.class, () -> orderService.createOrder(memberId, request));
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("존재하지 않는 스토어일 경우 예외 발생")
    void createOrder_StoreNotFound() {
        // given
        Long memberId = 1L;
        Long storeId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(storeId, List.of());

        Member member = mock(Member.class);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(storeRepository.findById(storeId)).willReturn(Optional.empty());

        // when & then
        BusinessException exception =
                assertThrows(BusinessException.class, () -> orderService.createOrder(memberId, request));
        assertEquals(ErrorCode.STORE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("존재하지 않는 메뉴일 경우 예외 발생")
    void createOrder_MenuNotFound() {
        // given
        Long memberId = 1L;
        Long storeId = 1L;
        Long menuId = 1L;

        OrderItemRequest itemRequest = new OrderItemRequest(menuId, 2L);
        CreateOrderRequest request = new CreateOrderRequest(storeId, List.of(itemRequest));

        Member member = mock(Member.class);
        Store store = new Store(null, "Test Store");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
        given(menuStockRepository.findAllByStoreIdAndMenuIdIn(storeId, List.of(menuId)))
                .willReturn(List.of());

        // when & then
        BusinessException exception =
                assertThrows(BusinessException.class, () -> orderService.createOrder(memberId, request));
        assertEquals(ErrorCode.MENU_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("재고가 부족할 경우 예외 발생")
    void createOrder_OutOfStock() {
        // given
        Long memberId = 1L;
        Long storeId = 1L;
        Long menuId = 1L;
        Long quantity = 20L; // stock is 10

        OrderItemRequest itemRequest = new OrderItemRequest(menuId, quantity);
        CreateOrderRequest request = new CreateOrderRequest(storeId, List.of(itemRequest));

        Member member = mock(Member.class);
        Store store = new Store(null, "Test Store");
        Menu menu = new Menu("Popcorn", 5000L);
        MenuStock menuStock = new MenuStock(menu, store, 10L);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
        given(menuStockRepository.findAllByStoreIdAndMenuIdIn(storeId, List.of(menuId)))
                .willReturn(List.of(menuStock));

        // when & then
        BusinessException exception =
                assertThrows(BusinessException.class, () -> orderService.createOrder(memberId, request));
        assertEquals(ErrorCode.OUT_OF_STOCK, exception.getErrorCode());
    }
}
