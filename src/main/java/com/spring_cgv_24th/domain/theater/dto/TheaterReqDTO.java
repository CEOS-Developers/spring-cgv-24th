package com.spring_cgv_24th.domain.theater.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TheaterReqDTO {

    private TheaterReqDTO() {
    }

    public record CreateTheaterReqDTO(

            @NotBlank(message = "영화관 이름은 필수입니다.")
            @Size(max = 100, message = "영화관 이름은 100자 이하이어야 합니다.")
            String name,

            @NotBlank(message = "영화관 주소는 필수입니다.")
            @Size(max = 255, message = "영화관 주소는 255자 이하이어야 합니다.")
            String address
    ) {
    }
}
