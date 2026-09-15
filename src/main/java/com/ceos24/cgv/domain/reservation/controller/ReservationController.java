package com.ceos24.cgv.domain.reservation.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("api/reservations")
@RestController
public class ReservationController {

    @PostMapping
    public void reserveSeat(
            @RequestHeader("X-Member-Id") Long memberId
    ) {

    }
}
