package com.ceos24.cgv.domain.theater.dto.request;

import com.ceos24.cgv.domain.theater.domain.ScreenType;

public record ScreenCreateRequest (
    ScreenType screenType,
    String name,
    Long totalSeats
){
}
