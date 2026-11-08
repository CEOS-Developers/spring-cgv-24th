package com.spring_cgv_24th.domain.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PaymentPartialCancelReqDTO(
        @NotNull UUID requestId,
        @NotBlank @Size(max = 200) String cancelReason,
        List<@NotNull @Valid Item> items,
        List<@NotNull @Positive Long> screeningSeatIds
) {
    public PaymentPartialCancelReqDTO {
        items = items == null ? List.of() : items;
        screeningSeatIds = screeningSeatIds == null ? List.of() : screeningSeatIds;
    }

    public record Item(@NotNull @Positive Long productId, @Positive int quantity) {
    }
}
