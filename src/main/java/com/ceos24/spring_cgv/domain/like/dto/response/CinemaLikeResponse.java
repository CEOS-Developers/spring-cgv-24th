package com.ceos24.spring_cgv.domain.like.dto.response;

import com.ceos24.spring_cgv.domain.like.entity.CinemaLike;
import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "영화관 찜 응답")
public record CinemaLikeResponse(

        @Schema(description = "찜 ID", example = "1")
        Long likeId,

        @Schema(description = "영화관 ID", example = "1")
        Long cinemaId,

        @Schema(description = "영화관 이름", example = "CGV 강남")
        String cinemaName,

        @Schema(description = "지역", example = "강남")
        String region,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        String address,

        @Schema(description = "찜한 시각", example = "2026-09-27T14:30:00")
        LocalDateTime likedAt
) {

    public static CinemaLikeResponse from(CinemaLike cinemaLike) {

        Cinema cinema = cinemaLike.getCinema();

        return CinemaLikeResponse.builder()
                .likeId(cinemaLike.getId())
                .cinemaId(cinema.getId())
                .cinemaName(cinema.getName())
                .region(cinema.getRegion())
                .address(cinema.getAddress())
                .likedAt(cinemaLike.getLikedAt())
                .build();
    }
}