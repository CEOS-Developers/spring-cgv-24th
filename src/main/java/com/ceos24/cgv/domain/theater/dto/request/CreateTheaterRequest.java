package com.ceos24.cgv.domain.theater.dto.request;

import com.ceos24.cgv.domain.store.dto.request.CreateStoreRequest;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "극장 생성 요청 DTO")
public record CreateTheaterRequest(
        @Schema(description = "극장 이름", example = "CGV 강남") String name, 
        @Schema(description = "극장 주소", example = "서울시 강남구") String address, 
        @Schema(description = "스토어 생성 요청") CreateStoreRequest createStoreRequest
) {}
