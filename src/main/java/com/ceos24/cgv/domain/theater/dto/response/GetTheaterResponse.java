package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.dto.TheaterInfo;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "극장 목록 조회 응답 DTO")
public record GetTheaterResponse(
        @Schema(description = "극장 목록") List<TheaterInfo> theaters
) {}
