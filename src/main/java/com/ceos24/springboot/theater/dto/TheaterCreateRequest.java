package com.ceos24.springboot.theater.dto;

import com.ceos24.springboot.theater.domain.Region;
import com.ceos24.springboot.theater.domain.Theater;

public record TheaterCreateRequest(
        String theaterName,
        Region region
) {

    public Theater toEntity() {
        return Theater.builder()
                .theaterName(theaterName)
                .region(region)
                .build();
    }
}