package com.ceos24.cgv.domain.theater.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TheaterCreateRequest(
        @NotBlank(message = "영화관 이름은 필수입니다.")
        String name,

        @NotBlank(message = "지역은 필수입니다.")
        String region,

        @NotBlank(message = "주소는 필수입니다.")
        String address,

        String description,

        String theaterImageUrl
) {
}
