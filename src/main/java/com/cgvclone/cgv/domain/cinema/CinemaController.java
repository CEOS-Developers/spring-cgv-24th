package com.cgvclone.cgv.domain.cinema;

import static org.springframework.http.HttpStatus.OK;

import com.cgvclone.cgv.domain.cinema.dto.CinemaDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    @GetMapping("/{cinemaId}")
    public ResponseEntity<CinemaDetailResponse> getCinema(@PathVariable Long cinemaId) {
        CinemaDetailResponse cinemaDetailResponse = cinemaService.getCinema(cinemaId);
        return ResponseEntity
                .status(OK)
                .body(cinemaDetailResponse);
    }
}
