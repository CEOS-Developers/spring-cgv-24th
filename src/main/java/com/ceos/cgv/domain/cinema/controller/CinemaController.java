package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.dto.CinemaResponse;
import com.ceos.cgv.domain.cinema.service.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {
    private final CinemaService cinemaService;

    @GetMapping
    public List<CinemaResponse> findAll(){
        return cinemaService.findAll()
                .stream()
                .map(CinemaResponse::from)
                .toList();
    }

    @GetMapping("/{cinemaId}")
    public CinemaResponse findById(@PathVariable Long cinemaId) {
        return CinemaResponse.from(cinemaService.findById(cinemaId));
    }
}
