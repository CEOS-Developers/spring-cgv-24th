package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.enums.TheaterRegion;

public record TheaterListResponse(
        Long theaterId,
        String name,
        String address,
        TheaterRegion region
) {

    public static TheaterListResponse from(Theater theater) {
        return new TheaterListResponse(
                theater.getId(),
                theater.getName(),
                theater.getAddress(),
                theater.getRegion()
        );
    }
}