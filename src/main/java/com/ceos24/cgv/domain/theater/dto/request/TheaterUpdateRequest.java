package com.ceos24.cgv.domain.theater.dto.request;

import jakarta.validation.constraints.Pattern;

public record TheaterUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "영화관 이름은 공백일 수 없습니다.")
        String name,

        String region,

        String address,

        String description,

        String theaterImageUrl
) {
}
