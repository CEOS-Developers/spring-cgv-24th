package com.ceos24.cgv.domain.movie.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "영화 생성 요청 DTO")
public record CreateMovieRequest(
        @NotBlank(message = "영화 제목은 필수입니다.") @Schema(description = "영화 제목", example = "인셉션") String title) {}
