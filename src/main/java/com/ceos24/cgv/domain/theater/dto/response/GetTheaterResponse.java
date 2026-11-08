package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.dto.TheaterInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "영화관 목록 조회 응답 DTO")
public record GetTheaterResponse(@Schema(description = "영화관 목록") List<TheaterInfo> theaters) {}
