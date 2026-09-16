package com.ceos24.cgv.domain.store.dto.response;

import java.util.List;

public record StoreResponse(
        Long storeId,
        String storeName,
        List<MenuStockInfo> menuStocks
) {}
