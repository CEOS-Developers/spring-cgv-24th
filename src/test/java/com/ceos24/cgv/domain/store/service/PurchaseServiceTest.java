package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.store.dto.request.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.request.PurchaseItemRequest;
import com.ceos24.cgv.domain.store.dto.response.ProductStockResponse;
import com.ceos24.cgv.domain.store.entity.CinemaStock;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseItemRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private CinemaStockRepository cinemaStockRepository;

    @Mock
    private PurchaseItemRepository purchaseItemRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void 영화관의_상품과_재고를_조회한다() {
        Cinema cinema = cinema(1L);
        Product product = product(10L, "고소팝콘(M)", 5_000);
        CinemaStock stock = CinemaStock.create(cinema, product, 100);

        when(cinemaRepository.existsById(1L)).thenReturn(true);
        when(cinemaStockRepository.findAllByCinemaIdOrderByProductIdAsc(1L))
                .thenReturn(List.of(stock));

        List<ProductStockResponse> result = purchaseService.getProductsByCinema(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().productId());
        assertEquals("고소팝콘(M)", result.getFirst().name());
        assertEquals(100, result.getFirst().quantity());
    }

    @Test
    void 구매하면_구매내역과_상품을_저장하고_재고를_차감한다() {
        User user = user(1L);
        Cinema cinema = cinema(2L);
        Product product = product(10L, "고소팝콘(M)", 5_000);
        CinemaStock stock = CinemaStock.create(cinema, product, 10);
        PurchaseCreateRequest request = new PurchaseCreateRequest(
                2L,
                List.of(new PurchaseItemRequest(10L, 2))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cinemaRepository.findById(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(
                2L,
                List.of(10L)
        )).thenReturn(List.of(stock));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            ReflectionTestUtils.setField(purchase, "id", 20L);
            return purchase;
        });

        Long result = purchaseService.createPurchase(1L, request);

        assertEquals(20L, result);
        assertEquals(8, stock.getQuantity());
        verify(purchaseRepository).save(any(Purchase.class));
        verify(purchaseItemRepository).saveAll(any());
    }

    @Test
    void 재고가_부족하면_구매를_저장하거나_재고를_차감하지_않는다() {
        User user = user(1L);
        Cinema cinema = cinema(2L);
        Product product = product(10L, "고소팝콘(M)", 5_000);
        CinemaStock stock = CinemaStock.create(cinema, product, 1);
        PurchaseCreateRequest request = new PurchaseCreateRequest(
                2L,
                List.of(new PurchaseItemRequest(10L, 2))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cinemaRepository.findById(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(
                2L,
                List.of(10L)
        )).thenReturn(List.of(stock));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> purchaseService.createPurchase(1L, request)
        );

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
        assertEquals(1, stock.getQuantity());
        verify(purchaseRepository, never()).save(any());
        verify(purchaseItemRepository, never()).saveAll(any());
    }

    @Test
    void 다른_사용자의_구매내역은_조회할_수_없다() {
        Purchase purchase = Purchase.create(user(2L), cinema(1L));
        ReflectionTestUtils.setField(purchase, "id", 20L);
        when(purchaseRepository.findById(20L)).thenReturn(Optional.of(purchase));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> purchaseService.getPurchase(1L, 20L)
        );

        assertEquals(ErrorCode.PURCHASE_ACCESS_DENIED, exception.getErrorCode());
        verify(purchaseItemRepository, never())
                .findAllByPurchaseIdOrderByIdAsc(any());
    }

    private User user(Long id) {
        User user = User.create("테스트 사용자");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Cinema cinema(Long id) {
        Cinema cinema = Cinema.create("CGV 강남", "서울시 강남구", "서울");
        ReflectionTestUtils.setField(cinema, "id", id);
        return cinema;
    }

    private Product product(Long id, String name, int price) {
        Product product = Product.create(name, price, "상품 설명");
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
