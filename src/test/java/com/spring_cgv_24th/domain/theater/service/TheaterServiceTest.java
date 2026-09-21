package com.spring_cgv_24th.domain.theater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.store.entity.Product;
import com.spring_cgv_24th.domain.store.entity.TheaterStock;
import com.spring_cgv_24th.domain.store.repository.ProductRepository;
import com.spring_cgv_24th.domain.store.repository.TheaterStockRepository;
import com.spring_cgv_24th.domain.theater.dto.TheaterReqDTO;
import com.spring_cgv_24th.domain.theater.dto.TheaterResDTO;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {

    @Mock private TheaterRepository theaterRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TheaterStockRepository theaterStockRepository;
    @Captor private ArgumentCaptor<Iterable<TheaterStock>> stocksCaptor;
    @InjectMocks private TheaterService theaterService;

    @Test
    void newTheaterGetsOneUnitForEveryProduct() {
        Product popcorn = mock(Product.class);
        Product cola = mock(Product.class);
        when(theaterRepository.save(any(Theater.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findAll(Sort.by("id"))).thenReturn(List.of(popcorn, cola));

        TheaterResDTO response = theaterService.createTheater(
                new TheaterReqDTO.CreateTheaterReqDTO("홍대", "서울 마포구"));

        List<TheaterStock> stocks = new ArrayList<>();
        verify(theaterStockRepository).saveAll(stocksCaptor.capture());
        stocksCaptor.getValue().forEach(stocks::add);
        assertEquals("홍대", response.name());
        assertEquals("서울 마포구", response.address());
        assertEquals(2, stocks.size());
        assertSame(popcorn, stocks.get(0).getProduct());
        assertSame(cola, stocks.get(1).getProduct());
        assertEquals(1, stocks.get(0).getQuantity());
        assertEquals(1, stocks.get(1).getQuantity());
        assertEquals("홍대", stocks.get(0).getTheater().getName());
    }
}
