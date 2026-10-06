package com.ceos24.springboot.theater.dto;

import com.ceos24.springboot.theater.domain.ScreenType;

// 상영관
public record ScreenCreateRequest(
        Long theaterId,
        String screenName,
        ScreenType screenType,
        Integer rowCount,
        Integer colCount
) {
}