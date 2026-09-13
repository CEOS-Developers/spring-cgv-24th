package com.ceos24.cgv.domain.theater.dto;

import com.ceos24.cgv.domain.theater.domain.Theater;

public record TheaterInfo (
        String theaterName,
        String address
){
    public static TheaterInfo from(Theater theater) {
        return new TheaterInfo(theater.getName(), theater.getAddress());
    }
}
