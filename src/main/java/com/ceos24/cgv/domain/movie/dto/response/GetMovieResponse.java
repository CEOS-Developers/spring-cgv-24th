package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "영화 목록 조회 응답 DTO")
public record GetMovieResponse(@Schema(description = "영화 목록") List<MovieInfo> movies) {}
