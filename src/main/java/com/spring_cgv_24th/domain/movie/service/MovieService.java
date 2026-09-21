package com.spring_cgv_24th.domain.movie.service;

import com.spring_cgv_24th.domain.movie.dto.MovieReqDTO;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional
    public MovieResDTO createMovie(MovieReqDTO.CreateMovieDTO request) {
        Movie movie = Movie.builder()
                .title(request.title().strip())
                .description(request.description())
                .durationMinutes(request.durationMinutes().shortValue())
                .ageRating(request.ageRating().strip())
                .releaseDate(request.releaseDate())
                .posterUrl(request.posterUrl())
                .build();

        return MovieResDTO.from(movieRepository.save(movie));
    }

    public MovieResDTO getMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new CustomException(ErrorCode.MOVIE_NOT_FOUND));
        return MovieResDTO.from(movie);
    }

    public List<MovieResDTO> getMovies() {
        return movieRepository.findAll(Sort.by("id")).stream()
                .map(MovieResDTO::from)
                .toList();
    }
}
