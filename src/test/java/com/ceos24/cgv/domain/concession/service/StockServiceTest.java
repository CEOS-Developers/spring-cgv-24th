package com.ceos24.cgv.domain.concession.service;

import com.ceos24.cgv.domain.concession.dto.request.StockRegisterRequest;
import com.ceos24.cgv.domain.concession.dto.response.StockResponse;
import com.ceos24.cgv.domain.concession.entity.Stock;
import com.ceos24.cgv.domain.concession.exception.ConcessionErrorStatus;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import com.ceos24.cgv.domain.concession.repository.StockRepository;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private StockService stockService;

    @Test
    void 기존_재고의_수량을_수정한다() {
        Stock existing = stock(10L, theater(1L), item(2L, "팝콘", 6_000), 10);
        when(stockRepository.findByTheaterIdAndItemId(1L, 2L)).thenReturn(Optional.of(existing));

        StockService.StockUpsertResult result = stockService.registerOrUpdate(1L, new StockRegisterRequest(2L, 30));

        assertThat(result).isEqualTo(new StockService.StockUpsertResult(10L, false));
        assertThat(existing.getQuantity()).isEqualTo(30);
        verify(stockRepository, never()).save(any());
    }

    @Test
    void 재고가_없으면_영화관별_재고를_생성한다() {
        var theater = theater(1L);
        var item = item(2L, "팝콘", 6_000);
        when(stockRepository.findByTheaterIdAndItemId(1L, 2L)).thenReturn(Optional.empty());
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(theater));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item));
        when(stockRepository.save(any(Stock.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        StockService.StockUpsertResult result = stockService.registerOrUpdate(1L, new StockRegisterRequest(2L, 20));

        assertThat(result).isEqualTo(new StockService.StockUpsertResult(10L, true));
    }

    @Test
    void 존재하지_않는_영화관의_재고는_조회할_수_없다() {
        when(theaterRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.findByTheaterId(999L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(TheaterErrorStatus.THEATER_NOT_FOUND);
    }

    @Test
    void 영화관별_재고_목록을_응답으로_변환한다() {
        var theater = theater(1L);
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(theater));
        when(stockRepository.findByTheaterIdWithItemOrderByItemNameAsc(1L))
                .thenReturn(List.of(stock(10L, theater, item(2L, "팝콘", 6_000), 20)));

        List<StockResponse> result = stockService.findByTheaterId(1L);

        assertThat(result).containsExactly(new StockResponse(2L, "팝콘", 6_000, 20));
    }
}
