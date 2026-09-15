package com.ceos.cgv.domain.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservedSeatRequest(
        @NotBlank @Pattern(regexp = "[A-Z]") String seatRow,
        @NotNull @Positive Integer seatNumber
) {
}
