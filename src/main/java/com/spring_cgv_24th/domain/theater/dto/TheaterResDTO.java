package com.spring_cgv_24th.domain.theater.dto;

import com.spring_cgv_24th.domain.theater.entity.Theater;

public record TheaterResDTO(
        Long theaterId,
        String name,
        String address
) {

    public static TheaterResDTO from(Theater theater) {
        return new TheaterResDTO(
                theater.getId(),
                theater.getName(),
                theater.getAddress());
    }
}
