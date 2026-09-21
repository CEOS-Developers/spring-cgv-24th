package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieUpdateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.dto.response.PersonResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieImage;
import com.ceos24.cgv.domain.movie.entity.MovieStatistics;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieImageRepository;
import com.ceos24.cgv.domain.movie.repository.MoviePersonRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieStatisticsRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;
    private final MovieImageRepository movieImageRepository;
    private final MovieStatisticsRepository movieStatisticsRepository;
    private final MoviePersonRepository moviePersonRepository;

    @Transactional
    public Long create(MovieCreateRequest request) {
        Movie movie = Movie.create(
                request.title(), request.genre(), request.runningTime(), request.description(),
                request.ageRating(), request.status(), request.openDate(), request.closeDate()
        );
        return movieRepository.save(movie).getId();
    }

    public List<MovieListResponse> findAll() {
        List<Movie> movies = movieRepository.findAll();
        List<Long> movieIds = movies.stream().map(Movie::getId).toList();

        Map<Long, String> posterUrlByMovieId = movieImageRepository.findByMovieIdIn(movieIds).stream()
                .filter(image -> image.getType() == MovieImageType.POSTER)
                .collect(Collectors.toMap(
                        image -> image.getMovie().getId(),
                        MovieImage::getMovieImageUrl,
                        (existing, duplicate) -> existing
                ));

        Map<Long, MovieStatistics> statisticsByMovieId = movieStatisticsRepository.findByMovieIdIn(movieIds).stream()
                .collect(Collectors.toMap(stat -> stat.getMovie().getId(), stat -> stat));

        return movies.stream()
                .map(movie -> {
                    String moviePosterUrl = posterUrlByMovieId.get(movie.getId());
                    MovieStatistics statistics = statisticsByMovieId.get(movie.getId());

                    return MovieListResponse.of(
                            movie, moviePosterUrl,
                            statistics != null ? statistics.getAudienceCount() : null,
                            statistics != null ? statistics.getReservationRate() : null,
                            statistics != null ? statistics.getEggScore() : null
                    );
                })
                .toList();
    }

    public MovieDetailResponse findById(Long id) {
        Movie movie = getMovieOrThrow(id);

        List<MovieImage> movieImages = movieImageRepository.findByMovieId(id);

        // 상단 대표 포스터
        String moviePosterUrl = movieImages.stream()
                .filter(image -> image.getType() == MovieImageType.POSTER)
                .findFirst()
                .map(MovieImage::getMovieImageUrl)
                .orElse(null);

        // 하단 포스터/스틸컷 전체
        List<String> movieImageUrls = movieImages.stream()
                .map(MovieImage::getMovieImageUrl)
                .toList();

        Optional<MovieStatistics> statistics = movieStatisticsRepository.findByMovieId(id);

        List<PersonResponse> persons = moviePersonRepository.findByMovieIdWithPerson(id).stream()
                .map(PersonResponse::from)
                .toList();

        return MovieDetailResponse.of(
                movie, moviePosterUrl, movieImageUrls,
                statistics.map(MovieStatistics::getAudienceCount).orElse(null),
                statistics.map(MovieStatistics::getReservationRate).orElse(null),
                statistics.map(MovieStatistics::getEggScore).orElse(null),
                persons
        );
    }

    @Transactional
    public void update(Long id, MovieUpdateRequest request) {
        Movie movie = getMovieOrThrow(id);
        movie.update(
                request.title(), request.genre(), request.runningTime(), request.description(),
                request.ageRating(), request.status(), request.openDate(), request.closeDate()
        );
    }

    @Transactional
    public void delete(Long id) {
        Movie movie = getMovieOrThrow(id);
        movieRepository.delete(movie);
    }

    private Movie getMovieOrThrow(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new GeneralException(MovieErrorStatus.MOVIE_NOT_FOUND));
    }
}
