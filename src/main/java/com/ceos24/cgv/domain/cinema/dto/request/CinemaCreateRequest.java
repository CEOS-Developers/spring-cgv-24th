package com.ceos24.cgv.domain.cinema.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CinemaCreateRequest(
        @NotBlank(message = "시네마 이름은 필수입니다.")
        @Size(max = 100, message = "시네마 이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @NotBlank(message = "지역 이름은 필수입니다.")
        @Size(max = 30, message = "지역 이름은 30자 이하여야 합니다.")
        String region
) {
}
