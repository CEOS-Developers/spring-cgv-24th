package com.spring_cgv_24th.domain.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.store.dto.StoreOrderReqDTO;
import com.spring_cgv_24th.domain.store.dto.StoreOrderResDTO;
import com.spring_cgv_24th.domain.store.dto.StoreStockReqDTO;
import com.spring_cgv_24th.domain.store.dto.TheaterStockResDTO;
import com.spring_cgv_24th.domain.store.entity.Product;
import com.spring_cgv_24th.domain.store.entity.StoreOrder;
import com.spring_cgv_24th.domain.store.entity.TheaterStock;
import com.spring_cgv_24th.domain.store.repository.ProductRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderItemRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderRepository;
import com.spring_cgv_24th.domain.store.repository.TheaterStockRepository;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private TheaterStockRepository theaterStockRepository;
    @Mock private TheaterRepository theaterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StoreOrderRepository storeOrderRepository;
    @Mock private StoreOrderItemRepository storeOrderItemRepository;
    @InjectMocks private StoreService storeService;

    @Test
    void sameMenuHasDifferentStockByTheater() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getName()).thenReturn("팝콘");
        when(product.getPrice()).thenReturn(9000);
        when(theaterRepository.existsById(1L)).thenReturn(true);
        when(theaterRepository.existsById(2L)).thenReturn(true);
        when(theaterStockRepository.findAllByTheater_IdOrderByProduct_IdAsc(1L))
                .thenReturn(List.of(stock(product, 8)));
        when(theaterStockRepository.findAllByTheater_IdOrderByProduct_IdAsc(2L))
                .thenReturn(List.of(stock(product, 3)));

        List<TheaterStockResDTO> first = storeService.getTheaterProducts(1L);
        List<TheaterStockResDTO> second = storeService.getTheaterProducts(2L);

        assertEquals(1L, first.getFirst().productId());
        assertEquals(first.getFirst().productId(), second.getFirst().productId());
        assertEquals(first.getFirst().name(), second.getFirst().name());
        assertEquals(8, first.getFirst().quantity());
        assertEquals(3, second.getFirst().quantity());
    }

    @Test
    void productWithoutTheaterStockIsNotListed() {
        Product existingProduct = mock(Product.class);
        when(existingProduct.getId()).thenReturn(1L);
        when(theaterRepository.existsById(1L)).thenReturn(true);
        when(theaterStockRepository.findAllByTheater_IdOrderByProduct_IdAsc(1L))
                .thenReturn(List.of(stock(existingProduct, 8)));

        List<TheaterStockResDTO> products = storeService.getTheaterProducts(1L);

        assertEquals(1, products.size());
        assertEquals(1L, products.getFirst().productId());
        assertEquals(8, products.getFirst().quantity());
        verifyNoInteractions(productRepository);
    }

    @Test
    void replenishmentAddsToExistingStock() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        TheaterStock stock = stock(product, 1);
        when(theaterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Theater.class)));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(theaterStockRepository.findByTheaterIdAndProductIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(stock));

        TheaterStockResDTO response = storeService.replenishStock(1L, 1L, new StoreStockReqDTO(3));

        assertEquals(4, stock.getQuantity());
        assertEquals(4, response.quantity());
    }

    @Test
    void firstReplenishmentRegistersStock() {
        Theater theater = mock(Theater.class);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(theaterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(theater));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(theaterStockRepository.findByTheaterIdAndProductIdForUpdate(1L, 1L))
                .thenReturn(Optional.empty());
        when(theaterStockRepository.save(any(TheaterStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TheaterStockResDTO response = storeService.replenishStock(1L, 1L, new StoreStockReqDTO(10));

        ArgumentCaptor<TheaterStock> stockCaptor = ArgumentCaptor.forClass(TheaterStock.class);
        verify(theaterStockRepository).save(stockCaptor.capture());
        assertSame(theater, stockCaptor.getValue().getTheater());
        assertSame(product, stockCaptor.getValue().getProduct());
        assertEquals(10, response.quantity());
    }

    @Test
    void replenishmentRejectsUnknownProduct() {
        when(theaterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Theater.class)));
        when(productRepository.findById(2L)).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class,
                () -> storeService.replenishStock(1L, 2L, new StoreStockReqDTO(10)));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(theaterStockRepository);
    }

    @Test
    void purchaseSavesPriceAndReducesStock() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getName()).thenReturn("팝콘");
        when(product.getPrice()).thenReturn(9000);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        Theater theater = mock(Theater.class);
        when(theater.getId()).thenReturn(1L);
        TheaterStock stock = stock(product, 3);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(theater));
        when(theaterStockRepository.findByTheaterIdAndProductIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(stock));
        when(storeOrderItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StoreOrderResDTO response = storeService.createOrder(1L, order(2));

        assertEquals(1, stock.getQuantity());
        assertEquals(18000L, response.totalPrice());
        assertEquals(9000, response.items().getFirst().unitPrice());
        verify(storeOrderRepository).save(org.mockito.ArgumentMatchers.any(StoreOrder.class));
        verify(storeOrderItemRepository).saveAll(anyList());
    }

    @Test
    void finalUnitCannotBePurchased() {
        TheaterStock stock = stock(mock(Product.class), 1);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(mock(Theater.class)));
        when(theaterStockRepository.findByTheaterIdAndProductIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(stock));

        CustomException error = assertThrows(CustomException.class,
                () -> storeService.createOrder(1L, order(1)));

        assertEquals(ErrorCode.STORE_STOCK_INSUFFICIENT, error.getErrorCode());
        assertEquals(1, stock.getQuantity());
        verifyNoInteractions(storeOrderRepository, storeOrderItemRepository);
    }

    @Test
    void purchaseCannotExceedAvailableStock() {
        TheaterStock stock = stock(mock(Product.class), 1);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(mock(Theater.class)));
        when(theaterStockRepository.findByTheaterIdAndProductIdForUpdate(1L, 1L))
                .thenReturn(Optional.of(stock));

        CustomException error = assertThrows(CustomException.class,
                () -> storeService.createOrder(1L, order(2)));

        assertEquals(ErrorCode.STORE_STOCK_INSUFFICIENT, error.getErrorCode());
        assertEquals(1, stock.getQuantity());
        verifyNoInteractions(storeOrderRepository, storeOrderItemRepository);
    }

    @Test
    void missingStockCannotBePurchased() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(mock(Theater.class)));
        when(theaterStockRepository.findByTheaterIdAndProductIdForUpdate(1L, 1L))
                .thenReturn(Optional.empty());
        when(productRepository.existsById(1L)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> storeService.createOrder(1L, order(1)));

        assertEquals(ErrorCode.THEATER_STOCK_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(storeOrderRepository, storeOrderItemRepository);
    }

    @Test
    void stockCannotBeRegisteredWithZeroQuantity() {
        CustomException error = assertThrows(CustomException.class,
                () -> stock(mock(Product.class), 0));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
    }

    @Test
    void replenishmentCannotOverflowQuantity() {
        TheaterStock stock = stock(mock(Product.class), Integer.MAX_VALUE);

        CustomException error = assertThrows(CustomException.class, () -> stock.addQuantity(1));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(Integer.MAX_VALUE, stock.getQuantity());
    }

    private static TheaterStock stock(Product product, int quantity) {
        return TheaterStock.builder()
                .theater(mock(Theater.class))
                .product(product)
                .quantity(quantity)
                .build();
    }

    private static StoreOrderReqDTO.CreateOrderDTO order(int quantity) {
        return new StoreOrderReqDTO.CreateOrderDTO(1L,
                List.of(new StoreOrderReqDTO.OrderItemDTO(1L, quantity)));
    }
}
