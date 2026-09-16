package com.ceos24.cgv.domain.concession.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemCreateRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        String name,

        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 양수여야 합니다.")
        Integer price
) {
}
