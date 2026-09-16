package com.ceos24.cgv.domain.concession.service;

import com.ceos24.cgv.domain.concession.dto.request.StockRegisterRequest;
import com.ceos24.cgv.domain.concession.dto.response.StockResponse;
import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Stock;
import com.ceos24.cgv.domain.concession.exception.ConcessionErrorStatus;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import com.ceos24.cgv.domain.concession.repository.StockRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;
    private final TheaterRepository theaterRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public StockUpsertResult registerOrUpdate(Long theaterId, StockRegisterRequest request) {
        return stockRepository.findByTheaterIdAndItemId(theaterId, request.itemId())
                .map(existing -> {
                    existing.update(request.quantity());
                    return new StockUpsertResult(existing.getId(), false);
                })
                .orElseGet(() -> {
                    Theater theater = theaterRepository.findById(theaterId)
                            .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_NOT_FOUND));
                    Item item = itemRepository.findById(request.itemId())
                            .orElseThrow(() -> new GeneralException(ConcessionErrorStatus.ITEM_NOT_FOUND));

                    Stock stock = Stock.create(theater, item, request.quantity());
                    Stock saved = stockRepository.save(stock);
                    return new StockUpsertResult(saved.getId(), true);
                });
    }

    public List<StockResponse> findByTheaterId(Long theaterId) {
        theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_NOT_FOUND));

        return stockRepository.findByTheaterIdWithItemOrderByItemNameAsc(theaterId).stream()
                .map(StockResponse::from)
                .toList();
    }

    public record StockUpsertResult(Long stockId, boolean isCreated) {
    }
}
