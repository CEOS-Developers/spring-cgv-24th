package com.ceos24.springboot.favorite.controller;

import com.ceos24.springboot.favorite.dto.TheaterFavoriteResponse;
import com.ceos24.springboot.favorite.service.TheaterFavoriteService;
import com.ceos24.springboot.user.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/favorites/theaters")
@Tag(name = "Theater Favorite", description = "영화관 찜 API")
public class TheaterFavoriteController {

    private final TheaterFavoriteService theaterFavoriteService;


    // 영화관 찜 등록
    @Operation(summary = "영화관 찜 등록")
    @PostMapping("/{theaterId}")
    public ResponseEntity<TheaterFavoriteResponse> addTheaterFavorite(
            @PathVariable Long theaterId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        TheaterFavoriteResponse response =
                theaterFavoriteService
                        .addTheaterFavorite(
                                userDetails.getUserId(),
                                theaterId
                        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // 찜한 영화관 목록
    @Operation(summary = "찜한 영화관 목록 조회")
    @GetMapping
    public ResponseEntity<List<TheaterFavoriteResponse>>
    getTheaterFavorites(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        return ResponseEntity.ok(
                theaterFavoriteService
                        .getTheaterFavorites(
                                userDetails.getUserId()
                        )
        );
    }


    // 영화관 찜 해제
    @Operation(summary = "영화관 찜 해제")
    @DeleteMapping("/{theaterId}")
    public ResponseEntity<Void> deleteTheaterFavorite(
            @PathVariable Long theaterId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        theaterFavoriteService
                .deleteTheaterFavorite(
                        userDetails.getUserId(),
                        theaterId
                );

        return ResponseEntity.noContent().build();
    }
}