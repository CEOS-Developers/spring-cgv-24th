package com.spring_cgv_24th.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelReqDTO(
        @NotBlank @Size(max = 200) String cancelReason
) {
}
