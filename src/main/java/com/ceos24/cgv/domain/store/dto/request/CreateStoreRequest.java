package com.ceos24.cgv.domain.store.dto.request;

import com.ceos24.cgv.domain.store.dto.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "매장 생성 요청 DTO")
public record CreateStoreRequest(
        @NotBlank(message = "매장 이름은 필수입니다.") @Schema(description = "매장 이름", example = "CGV 매점") String name,
        @NotEmpty(message = "메뉴 항목은 1개 이상이어야 합니다.") @Valid @Schema(description = "메뉴 항목 목록")
                List<MenuItem> menuItems) {}
