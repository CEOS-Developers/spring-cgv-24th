package com.spring_cgv_24th.domain.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class StoreOrderReqDTO {

    private StoreOrderReqDTO() {
    }

    public record CreateOrderDTO(
            @NotEmpty List<@NotNull @Valid OrderItemDTO> items
    ) {
    }

    public record OrderItemDTO(
            @NotNull @Positive Long productId,
            @NotNull @Positive Integer quantity
    ) {
    }
}
