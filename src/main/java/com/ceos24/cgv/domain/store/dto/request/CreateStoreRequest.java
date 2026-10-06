package com.ceos24.cgv.domain.store.dto.request;

import com.ceos24.cgv.domain.store.dto.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "매장 생성 요청 DTO")
public record CreateStoreRequest(
        @Schema(description = "매장 이름", example = "CGV 매점") String name,
        @Schema(description = "메뉴 항목 목록") List<MenuItem> menuItems) {}
