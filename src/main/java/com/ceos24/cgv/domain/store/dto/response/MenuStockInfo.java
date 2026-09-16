package com.ceos24.cgv.domain.store.dto.response;

public record MenuStockInfo(
        Long menuId,
        String name,
        Long price,
        Long stock
) {}
