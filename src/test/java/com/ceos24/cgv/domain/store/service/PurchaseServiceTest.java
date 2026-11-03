package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.store.dto.request.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.request.PurchaseItemRequest;
import com.ceos24.cgv.domain.store.dto.response.PurchaseCreateResponse;
import com.ceos24.cgv.domain.store.dto.response.PurchaseResponse;
import com.ceos24.cgv.domain.store.entity.CinemaStock;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseItemRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @InjectMocks
    private PurchaseService purchaseService;

    @Captor
    private ArgumentCaptor<Purchase> purchaseCaptor;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Captor
    private ArgumentCaptor<List<PurchaseItem>> purchaseItemsCaptor;

    @Test
    void 주문하면_서버가_금액을_확정하고_결제대기_주문과_재고를_확보한다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        Product product = product(10L, "고소팝콘(M)", 5_000);
        CinemaStock stock = CinemaStock.create(cinema, product, 10);
        PurchaseCreateRequest request = new PurchaseCreateRequest(
                2L,
                List.of(new PurchaseItemRequest(10L, 2))
        );

        mockAuthenticatedUser(userEntity);
        when(cinemaRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(
                2L,
                List.of(10L)
        )).thenReturn(List.of(stock));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            ReflectionTestUtils.setField(purchase, "id", 20L);
            return purchase;
        });

        PurchaseCreateResponse result = purchaseService.createPurchase(userEntity.getUsername(), request);

        assertEquals(20L, result.purchaseId());
        assertEquals(10_000L, result.totalAmount());
        assertEquals(PurchaseStatus.PENDING_PAYMENT, result.status());
        assertTrue(result.orderId().startsWith("cgv_store_"));
        assertTrue(result.orderId().length() <= 64);
        assertDoesNotThrow(() -> UUID.fromString(result.orderId().substring("cgv_store_".length())));
        assertEquals(Duration.ofMinutes(30), Duration.between(result.createdAt(), result.expiresAt()));
        assertEquals(8, stock.getQuantity());
        verify(purchaseRepository).save(purchaseCaptor.capture());
        assertEquals(result.orderId(), purchaseCaptor.getValue().getOrderId());
        assertEquals(result.totalAmount(), purchaseCaptor.getValue().getTotalAmount());
        verify(purchaseItemRepository).saveAll(purchaseItemsCaptor.capture());
        PurchaseItem savedItem = purchaseItemsCaptor.getValue().getFirst();
        assertEquals(purchaseCaptor.getValue(), savedItem.getPurchase());
        assertEquals(5_000, savedItem.getUnitPrice());
        assertEquals(2, savedItem.getQuantity());
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(purchaseCaptor.getValue(), paymentCaptor.getValue().getPurchase());
        assertEquals(PaymentStatus.READY, paymentCaptor.getValue().getStatus());
        assertEquals(result.totalAmount(), paymentCaptor.getValue().getTotalAmount());
        assertEquals(result.totalAmount(), paymentCaptor.getValue().getRemainingAmount());
    }

    @Test
    void 여러_상품의_단가와_수량을_합산하여_주문금액을_저장한다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        CinemaStock popcorn = CinemaStock.create(cinema, product(10L, "팝콘", 5_000), 10);
        CinemaStock drink = CinemaStock.create(cinema, product(20L, "음료", 3_000), 10);
        PurchaseCreateRequest request = new PurchaseCreateRequest(2L, List.of(
                new PurchaseItemRequest(20L, 1),
                new PurchaseItemRequest(10L, 2)
        ));

        mockAuthenticatedUser(userEntity);
        when(cinemaRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(2L, List.of(10L, 20L)))
                .thenReturn(List.of(popcorn, drink));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseCreateResponse result = purchaseService.createPurchase(userEntity.getUsername(), request);

        assertEquals(13_000L, result.totalAmount());
        assertEquals(8, popcorn.getQuantity());
        assertEquals(9, drink.getQuantity());
        verify(purchaseItemRepository).saveAll(purchaseItemsCaptor.capture());
        long itemTotal = purchaseItemsCaptor.getValue().stream()
                .mapToLong(item -> (long) item.getUnitPrice() * item.getQuantity())
                .sum();
        assertEquals(result.totalAmount(), itemTotal);
    }

    @Test
    void 주문금액은_int_범위를_초과해도_long으로_계산한다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        CinemaStock stock = CinemaStock.create(cinema, product(10L, "상품", 1_500_000_000), 2);
        PurchaseCreateRequest request = new PurchaseCreateRequest(2L, List.of(new PurchaseItemRequest(10L, 2)));

        mockAuthenticatedUser(userEntity);
        when(cinemaRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(2L, List.of(10L)))
                .thenReturn(List.of(stock));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseCreateResponse result = purchaseService.createPurchase(userEntity.getUsername(), request);

        assertEquals(3_000_000_000L, result.totalAmount());
        assertEquals(0, stock.getQuantity());
    }

    @Test
    void 결제할_금액이_0원이면_주문을_생성하거나_재고를_차감하지_않는다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        CinemaStock stock = CinemaStock.create(cinema, product(10L, "무료 상품", 0), 10);
        PurchaseCreateRequest request = new PurchaseCreateRequest(2L, List.of(new PurchaseItemRequest(10L, 1)));

        mockAuthenticatedUser(userEntity);
        when(cinemaRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(2L, List.of(10L)))
                .thenReturn(List.of(stock));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> purchaseService.createPurchase(userEntity.getUsername(), request));

        assertEquals(ErrorCode.INVALID_PURCHASE_AMOUNT, exception.getErrorCode());
        assertEquals(10, stock.getQuantity());
        verify(purchaseRepository, never()).save(any());
        verify(purchaseItemRepository, never()).saveAll(any());
    }

    @Test
    void 주문금액이_long_범위를_초과하면_주문과_재고를_변경하지_않는다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        List<CinemaStock> stocks = List.of(
                CinemaStock.create(cinema, product(10L, "상품1", Integer.MAX_VALUE), Integer.MAX_VALUE),
                CinemaStock.create(cinema, product(20L, "상품2", Integer.MAX_VALUE), Integer.MAX_VALUE),
                CinemaStock.create(cinema, product(30L, "상품3", Integer.MAX_VALUE), Integer.MAX_VALUE)
        );
        PurchaseCreateRequest request = new PurchaseCreateRequest(2L, List.of(
                new PurchaseItemRequest(10L, Integer.MAX_VALUE),
                new PurchaseItemRequest(20L, Integer.MAX_VALUE),
                new PurchaseItemRequest(30L, Integer.MAX_VALUE)
        ));

        mockAuthenticatedUser(userEntity);
        when(cinemaRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(2L, List.of(10L, 20L, 30L)))
                .thenReturn(stocks);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> purchaseService.createPurchase(userEntity.getUsername(), request));

        assertEquals(ErrorCode.INVALID_PURCHASE_AMOUNT, exception.getErrorCode());
        assertTrue(stocks.stream().allMatch(stock -> stock.getQuantity() == Integer.MAX_VALUE));
        verify(purchaseRepository, never()).save(any());
        verify(purchaseItemRepository, never()).saveAll(any());
    }

    @Test
    void 재고가_부족하면_구매를_저장하거나_재고를_차감하지_않는다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        Product product = product(10L, "고소팝콘(M)", 5_000);
        CinemaStock stock = CinemaStock.create(cinema, product, 1);
        PurchaseCreateRequest request = new PurchaseCreateRequest(
                2L,
                List.of(new PurchaseItemRequest(10L, 2))
        );

        mockAuthenticatedUser(userEntity);
        when(cinemaRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cinema));
        when(cinemaStockRepository.findAllByCinemaIdAndProductIdsForUpdate(
                2L,
                List.of(10L)
        )).thenReturn(List.of(stock));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> purchaseService.createPurchase(userEntity.getUsername(), request)
        );

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
        assertEquals(1, stock.getQuantity());
        verify(purchaseRepository, never()).save(any());
        verify(purchaseItemRepository, never()).saveAll(any());
    }

    @Test
    void 다른_사용자의_구매내역은_조회할_수_없다() {
        UserEntity authenticatedUser = user(1L);
        Purchase purchase = Purchase.create(user(2L), cinema(1L), 5_000);
        ReflectionTestUtils.setField(purchase, "id", 20L);
        mockAuthenticatedUser(authenticatedUser);
        when(purchaseRepository.findById(20L)).thenReturn(Optional.of(purchase));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> purchaseService.getPurchase(authenticatedUser.getUsername(), 20L)
        );

        assertEquals(ErrorCode.PURCHASE_ACCESS_DENIED, exception.getErrorCode());
        verify(purchaseItemRepository, never())
                .findAllByPurchaseIdOrderByIdAsc(any());
    }

    @Test
    void 상품가격이_바뀌어도_주문상세는_확정금액과_주문당시_가격을_반환한다() {
        UserEntity userEntity = user(1L);
        Cinema cinema = cinema(2L);
        Product product = product(10L, "팝콘", 5_000);
        Purchase purchase = Purchase.create(userEntity, cinema, 10_000);
        ReflectionTestUtils.setField(purchase, "id", 20L);
        PurchaseItem item = PurchaseItem.create(product, purchase, 2);
        ReflectionTestUtils.setField(product, "price", 9_000);

        mockAuthenticatedUser(userEntity);
        when(purchaseRepository.findById(20L)).thenReturn(Optional.of(purchase));
        when(purchaseItemRepository.findAllByPurchaseIdOrderByIdAsc(20L)).thenReturn(List.of(item));

        PurchaseResponse result = purchaseService.getPurchase(userEntity.getUsername(), 20L);

        assertEquals(10_000L, result.totalAmount());
        assertEquals(5_000, result.items().getFirst().unitPrice());
        assertEquals(purchase.getOrderId(), result.orderId());
        assertEquals(PurchaseStatus.PENDING_PAYMENT, result.status());
        assertEquals(purchase.getCreatedAt(), result.createdAt());
        assertEquals(purchase.getExpiresAt(), result.expiresAt());
    }

    private void mockAuthenticatedUser(UserEntity userEntity) {
        when(userRepository.findByUsernameAndIsLockAndIsSocial(
                userEntity.getUsername(),
                false,
                false
        )).thenReturn(Optional.of(userEntity));
    }

    private UserEntity user(Long id) {
        UserEntity userEntity = UserEntity.createLocalUser(
                "test-user-" + id,
                "encoded-password",
                "테스트 사용자"
        );
        ReflectionTestUtils.setField(userEntity, "id", id);
        return userEntity;
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
