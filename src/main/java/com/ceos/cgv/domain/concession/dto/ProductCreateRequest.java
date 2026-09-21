package com.ceos.cgv.domain.concession.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductCreateRequest(
        @NotBlank String name,
        @NotNull @Positive Long price,
        String description
) {
}
