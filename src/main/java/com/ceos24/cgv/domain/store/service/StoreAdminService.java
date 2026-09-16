package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.store.domain.Menu;
import com.ceos24.cgv.domain.store.domain.MenuStock;
import com.ceos24.cgv.domain.store.domain.Store;
import com.ceos24.cgv.domain.store.dto.request.CreateStoreRequest;
import com.ceos24.cgv.domain.store.repository.MenuRepository;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import com.ceos24.cgv.domain.theater.domain.Theater;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class StoreAdminService {

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MenuStockRepository menuStockRepository;

    @Transactional
    public void createStore(Theater theater, CreateStoreRequest request) {
        Store store = new Store(theater, request.name());
        storeRepository.save(store);

        List<MenuStock> menuStocks = new ArrayList<>();
        request.menuItems().forEach(item -> {
                    Menu menu = menuRepository.findById(item.menuId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
                    menuStocks.add(new MenuStock(menu, store, item.stock()));
                });
        menuStockRepository.saveAll(menuStocks);
    }
}
