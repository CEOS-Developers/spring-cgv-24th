package com.ceos24.cgv.domain.concession.dto.response;

import com.ceos24.cgv.domain.concession.entity.Item;

public record ItemResponse(
        Long id,
        String name,
        Integer price
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getPrice());
    }
}
