package com.ceos24.cgv.domain.theater.dto.request;

public record TheaterUpdateRequest(
        String name,
        String region,
        String address,
        String description,
        String theaterImageUrl
) {
}
