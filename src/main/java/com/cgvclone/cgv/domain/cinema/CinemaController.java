package com.cgvclone.cgv.domain.cinema;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import com.cgvclone.cgv.domain.cinema.dto.CinemaDetailResponse;
import com.cgvclone.cgv.domain.cinema.dto.CinemaListResponse;
import com.cgvclone.cgv.domain.cinema_keeping.CinemaKeepingService;
import com.cgvclone.cgv.domain.auth.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;
    private final CinemaKeepingService cinemaKeepingService;

    @GetMapping
    public ResponseEntity<CinemaListResponse> getCinemas(@RequestParam(required = false) Long regionId) {
        CinemaListResponse cinemaListResponse = cinemaService.getCinemas(regionId);
        return ResponseEntity
                .status(OK)
                .body(cinemaListResponse);
    }

    @GetMapping("/{cinemaId}")
    public ResponseEntity<CinemaDetailResponse> getCinema(@PathVariable Long cinemaId) {
        CinemaDetailResponse cinemaDetailResponse = cinemaService.getCinema(cinemaId);
        return ResponseEntity
                .status(OK)
                .body(cinemaDetailResponse);
    }

    @PostMapping("/{cinemaId}/keep")
    public ResponseEntity<Void> keepCinema(@PathVariable Long cinemaId,
            @AuthenticationPrincipal CustomUserDetails user) {
        cinemaKeepingService.keepCinema(user.getUserId(), cinemaId);
        return ResponseEntity
                .status(CREATED)
                .build();
    }

    @DeleteMapping("/{cinemaId}/keep")
    public ResponseEntity<Void> cancelCinemaKeeping(@PathVariable Long cinemaId,
            @AuthenticationPrincipal CustomUserDetails user) {
        cinemaKeepingService.cancelCinemaKeeping(user.getUserId(), cinemaId);
        return ResponseEntity
                .status(NO_CONTENT)
                .build();
    }
}
