package com.ceos24.cgv.domain.snack.service;

import com.ceos24.cgv.domain.snack.dto.request.SnackOrderCreateRequest;
import com.ceos24.cgv.domain.snack.dto.request.SnackOrderItemRequest;
import com.ceos24.cgv.domain.snack.dto.response.SnackOrderResponse;
import com.ceos24.cgv.domain.snack.entity.SnackItem;
import com.ceos24.cgv.domain.snack.entity.SnackOrder;
import com.ceos24.cgv.domain.snack.entity.SnackStock;
import com.ceos24.cgv.domain.snack.exception.SnackOrderException;
import com.ceos24.cgv.domain.snack.repository.SnackOrderRepository;
import com.ceos24.cgv.domain.snack.repository.SnackStockRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SnackOrderServiceTest {

    @Mock
    private SnackOrderRepository snackOrderRepository;

    @Mock
    private SnackStockRepository snackStockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TheaterRepository theaterRepository;

    @InjectMocks
    private SnackOrderService snackOrderService;

    @Test
    @DisplayName("매점 상품을 구매하면 주문이 저장되고 재고가 차감된다")
    void purchaseSnacks_success() {
        // given
        User user = mock(User.class);
        Theater theater = mock(Theater.class);
        SnackItem snackItem = mock(SnackItem.class);

        given(user.getId()).willReturn(1L);
        given(theater.getId()).willReturn(10L);
        given(theater.getName()).willReturn("CGV 강남");
        given(snackItem.getId()).willReturn(100L);
        given(snackItem.getName()).willReturn("고소팝콘");
        given(snackItem.getPrice()).willReturn(6000);

        SnackStock stock = SnackStock.builder()
                .stockQuantity(10)
                .snackItem(snackItem)
                .theater(theater)
                .build();

        SnackOrderCreateRequest request =
                new SnackOrderCreateRequest(
                        List.of(
                                new SnackOrderItemRequest(
                                        100L,
                                        2
                                )
                        )
                );

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(theaterRepository.findById(10L))
                .willReturn(Optional.of(theater));
        given(snackStockRepository.findAllForPurchase(
                10L,
                List.of(100L)
        )).willReturn(List.of(stock));

        given(snackOrderRepository.save(any(SnackOrder.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        // when
        SnackOrderResponse response =
                snackOrderService.purchaseSnacks(
                        1L,
                        10L,
                        request
                );

        // then
        assertThat(response.totalPrice()).isEqualTo(12000);
        assertThat(stock.getStockQuantity()).isEqualTo(8);
        assertThat(response.items()).hasSize(1);

        then(snackOrderRepository)
                .should()
                .save(any(SnackOrder.class));
    }

    @Test
    @DisplayName("재고보다 많은 수량을 구매하면 예외가 발생한다")
    void purchaseSnacks_insufficientStock() {
        // given
        User user = mock(User.class);
        Theater theater = mock(Theater.class);
        SnackItem snackItem = mock(SnackItem.class);

        given(snackItem.getId()).willReturn(100L);
        given(snackItem.getPrice()).willReturn(6000);

        SnackStock stock = SnackStock.builder()
                .stockQuantity(1)
                .snackItem(snackItem)
                .theater(theater)
                .build();

        SnackOrderCreateRequest request =
                new SnackOrderCreateRequest(
                        List.of(
                                new SnackOrderItemRequest(
                                        100L,
                                        2
                                )
                        )
                );

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(theaterRepository.findById(10L))
                .willReturn(Optional.of(theater));
        given(snackStockRepository.findAllForPurchase(
                10L,
                List.of(100L)
        )).willReturn(List.of(stock));

        // when & then
        assertThatThrownBy(() ->
                snackOrderService.purchaseSnacks(
                        1L,
                        10L,
                        request
                )
        ).isInstanceOf(SnackOrderException.class);

        assertThat(stock.getStockQuantity()).isEqualTo(1);

        then(snackOrderRepository)
                .should(never())
                .save(any(SnackOrder.class));
    }
}