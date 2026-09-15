package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.dto.ScreenInfo;

import java.util.List;

public record GetScreenResponse (
        Long theaterId,
        List<ScreenInfo> screens
){
}
