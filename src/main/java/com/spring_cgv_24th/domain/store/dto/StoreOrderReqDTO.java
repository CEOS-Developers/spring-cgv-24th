package com.spring_cgv_24th.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class StoreOrderReqDTO {

    private StoreOrderReqDTO() {
    }

    public record CreateOrderDTO(
            @Schema(description = "임시 회원 ID. 로그인 구현 후 요청에서 제거하고 인증된 회원 ID를 사용합니다.")
            @NotNull @Positive Long memberId,
            @NotEmpty List<@NotNull @Valid OrderItemDTO> items
    ) {
    }

    public record OrderItemDTO(
            @NotNull @Positive Long productId,
            @NotNull @Positive Integer quantity
    ) {
    }
}
