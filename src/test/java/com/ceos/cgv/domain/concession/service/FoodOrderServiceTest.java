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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        given(productRepository.findById(3L)).willReturn(Optional.of(product));
        given(inventoryRepository.findByCinema_IdAndProduct_Id(2L, 3L)).willReturn(Optional.of(inventory));
        given(foodOrderRepository.save(any(FoodOrder.class))).willAnswer(invocation -> invocation.getArgument(0));

        FoodOrder result = foodOrderService.create(new FoodOrderCreateRequest(
                1L, 2L, List.of(new FoodOrderItemRequest(3L, 2))
        ));

        assertThat(result.getTotalPrice()).isEqualTo(2400L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(inventory.getStockQuantity()).isEqualTo(3);
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
        given(productRepository.findById(3L)).willReturn(Optional.of(product));
        given(inventoryRepository.findByCinema_IdAndProduct_Id(2L, 3L)).willReturn(Optional.of(inventory));

        assertThatThrownBy(() -> foodOrderService.create(new FoodOrderCreateRequest(
                1L, 2L, List.of(new FoodOrderItemRequest(3L, 2))
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH);
    }
}
