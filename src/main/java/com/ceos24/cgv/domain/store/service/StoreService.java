package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.store.dto.response.MenuStockInfo;
import com.ceos24.cgv.domain.store.dto.response.StoreResponse;
import com.ceos24.cgv.domain.store.entity.MenuStock;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final MenuStockRepository menuStockRepository;

    @Transactional(readOnly = true)
    public StoreResponse getStoreInfo(Long theaterId) {
        Store store = storeRepository
                .findByTheaterId(theaterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        List<MenuStock> menuStocks = menuStockRepository.findByStoreIdWithMenu(store.getId());

        List<MenuStockInfo> menuStockInfos = menuStocks.stream()
                .map(ms -> new MenuStockInfo(
                        ms.getMenu().getId(),
                        ms.getMenu().getName(),
                        ms.getMenu().getPrice(),
                        ms.getStock()))
                .toList();

        return new StoreResponse(store.getId(), store.getName(), menuStockInfos);
    }
}
