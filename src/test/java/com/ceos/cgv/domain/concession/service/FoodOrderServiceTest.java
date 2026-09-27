package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos.cgv.domain.concession.dto.FoodOrderCreateRequest;
import com.ceos.cgv.domain.concession.dto.FoodOrderItemRequest;
import com.ceos.cgv.domain.concession.entity.FoodOrder;
import com.ceos.cgv.domain.concession.entity.Inventory;
import com.ceos.cgv.domain.concession.entity.Product;
import com.ceos.cgv.domain.concession.repository.FoodOrderRepository;
import com.ceos.cgv.domain.concession.repository.InventoryRepository;
import com.ceos.cgv.domain.concession.repository.ProductRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FoodOrderServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CinemaRepository cinemaRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private FoodOrderRepository foodOrderRepository;
    @InjectMocks
    private FoodOrderService foodOrderService;

    @Test
    void 상품_가격과_수량으로_총액을_계산하고_재고를_차감한다() {
        User user = mock(User.class);
        Cinema cinema = mock(Cinema.class);
        Product product = mock(Product.class);
        when(cinema.getId()).thenReturn(2L);
        when(product.getId()).thenReturn(3L);
        when(product.getPrice()).thenReturn(1200L);
        Inventory inventory = new Inventory(cinema, product, 5);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(cinemaRepository.findById(2L)).willReturn(Optional.of(cinema));
        given(productRepository.findAllById(Set.of(3L))).willReturn(List.of(product));
        given(inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(2L, 3L)).willReturn(Optional.of(inventory));
        given(foodOrderRepository.save(any(FoodOrder.class))).willAnswer(invocation -> invocation.getArgument(0));

        FoodOrder result = foodOrderService.create(new FoodOrderCreateRequest(
                1L, 2L, List.of(new FoodOrderItemRequest(3L, 2))
        ));

        assertThat(result.getTotalPrice()).isEqualTo(2400L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(inventory.getStockQuantity()).isEqualTo(3);
        then(productRepository).should().findAllById(Set.of(3L));
    }

    @Test
    void 재고가_부족하면_주문을_저장하지_않는다() {
        User user = mock(User.class);
        Cinema cinema = mock(Cinema.class);
        Product product = mock(Product.class);
        when(cinema.getId()).thenReturn(2L);
        when(product.getId()).thenReturn(3L);
        Inventory inventory = new Inventory(cinema, product, 1);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(cinemaRepository.findById(2L)).willReturn(Optional.of(cinema));
        given(productRepository.findAllById(Set.of(3L))).willReturn(List.of(product));
        given(inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(2L, 3L)).willReturn(Optional.of(inventory));

        assertThatThrownBy(() -> foodOrderService.create(new FoodOrderCreateRequest(
                1L, 2L, List.of(new FoodOrderItemRequest(3L, 2))
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);
    }

    @Test
    void 중복_수량은_합산하고_재고는_id_순서로_잠그며_주문항목은_입력_순서를_유지한다() {
        User user = mock(User.class);
        Cinema cinema = mock(Cinema.class);
        Product product3 = mock(Product.class);
        Product product1 = mock(Product.class);
        when(cinema.getId()).thenReturn(2L);
        when(product3.getId()).thenReturn(3L);
        when(product3.getPrice()).thenReturn(1200L);
        when(product1.getId()).thenReturn(1L);
        when(product1.getPrice()).thenReturn(1000L);

        Inventory inventory3 = new Inventory(cinema, product3, 5);
        Inventory inventory1 = new Inventory(cinema, product1, 5);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(cinemaRepository.findById(2L)).willReturn(Optional.of(cinema));
        given(productRepository.findAllById(Set.of(3L, 1L)))
                .willReturn(List.of(product3, product1));
        given(inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(2L, 1L))
                .willReturn(Optional.of(inventory1));
        given(inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(2L, 3L))
                .willReturn(Optional.of(inventory3));
        given(foodOrderRepository.save(any(FoodOrder.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodOrder result = foodOrderService.create(new FoodOrderCreateRequest(
                1L, 2L, List.of(
                        new FoodOrderItemRequest(3L, 1),
                        new FoodOrderItemRequest(1L, 2),
                        new FoodOrderItemRequest(3L, 2))));

        InOrder lockOrder = inOrder(inventoryRepository);
        lockOrder.verify(inventoryRepository).findByCinema_IdAndProduct_IdForUpdate(2L, 1L);
        lockOrder.verify(inventoryRepository).findByCinema_IdAndProduct_IdForUpdate(2L, 3L);
        then(inventoryRepository).shouldHaveNoMoreInteractions();

        assertThat(result.getTotalPrice()).isEqualTo(5600L);
        assertThat(result.getItems())
                .extracting(item -> item.getProduct().getId(), item -> item.getQuantity())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3L, 3),
                        org.assertj.core.groups.Tuple.tuple(1L, 2));
        assertThat(inventory3.getStockQuantity()).isEqualTo(2);
        assertThat(inventory1.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void 잠금_순서상_두번째_상품의_재고가_부족하면_첫번째_재고와_주문을_변경하지_않는다() {
        User user = mock(User.class);
        Cinema cinema = mock(Cinema.class);
        Product product3 = mock(Product.class);
        Product product1 = mock(Product.class);
        when(cinema.getId()).thenReturn(2L);
        when(product3.getId()).thenReturn(3L);
        when(product1.getId()).thenReturn(1L);

        Inventory inventory3 = new Inventory(cinema, product3, 2);
        Inventory inventory1 = new Inventory(cinema, product1, 5);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(cinemaRepository.findById(2L)).willReturn(Optional.of(cinema));
        given(productRepository.findAllById(Set.of(3L, 1L)))
                .willReturn(List.of(product3, product1));
        given(inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(2L, 1L))
                .willReturn(Optional.of(inventory1));
        given(inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(2L, 3L))
                .willReturn(Optional.of(inventory3));

        assertThatThrownBy(() -> foodOrderService.create(new FoodOrderCreateRequest(
                1L, 2L, List.of(
                        new FoodOrderItemRequest(3L, 1),
                        new FoodOrderItemRequest(1L, 2),
                        new FoodOrderItemRequest(3L, 2)))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);

        InOrder lockOrder = inOrder(inventoryRepository);
        lockOrder.verify(inventoryRepository).findByCinema_IdAndProduct_IdForUpdate(2L, 1L);
        lockOrder.verify(inventoryRepository).findByCinema_IdAndProduct_IdForUpdate(2L, 3L);
        assertThat(inventory1.getStockQuantity()).isEqualTo(5);
        assertThat(inventory3.getStockQuantity()).isEqualTo(2);
        then(foodOrderRepository).should(never()).save(any(FoodOrder.class));
    }
}
