package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/theaters")
@RequiredArgsConstructor
@RestController
public class TheaterController {

    private final TheaterService theaterService;

    @GetMapping
    public GetTheaterResponse getTheaters() {
        return theaterService.getTheaters();
    }
}
