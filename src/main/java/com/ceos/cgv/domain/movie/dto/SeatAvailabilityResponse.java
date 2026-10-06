package com.ceos.cgv.domain.movie.dto;

public record SeatAvailabilityResponse(String seatRow, Integer seatNumber, Status status) {
    public enum Status {
        AVAILABLE,
        HELD,
        RESERVED
    }
}
