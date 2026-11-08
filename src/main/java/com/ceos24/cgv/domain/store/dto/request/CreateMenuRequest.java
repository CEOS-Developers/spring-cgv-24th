package com.ceos24.cgv.domain.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 생성 요청 DTO")
public record CreateMenuRequest(
        @Schema(description = "메뉴 이름", example = "팝콘") String name,
        @Schema(description = "메뉴 가격", example = "5000") Long price) {}
