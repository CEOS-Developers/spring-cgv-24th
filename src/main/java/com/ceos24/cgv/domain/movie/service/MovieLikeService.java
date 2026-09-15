package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.response.MovieLikeResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieLike;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieLikeService {

    private final MovieLikeRepository movieLikeRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    @Transactional
    public MovieLikeResponse like(Long userId, Long movieId) {
        movieLikeRepository.findByUserIdAndMovieId(userId, movieId)
                .ifPresent(like -> {
                    throw new GeneralException(MovieErrorStatus.ALREADY_LIKED_MOVIE);
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new GeneralException(MovieErrorStatus.MOVIE_NOT_FOUND));

        MovieLike saved = movieLikeRepository.save(MovieLike.create(user, movie));
        return MovieLikeResponse.from(saved);
    }

    @Transactional
    public void unlike(Long userId, Long movieId) {
        MovieLike movieLike = movieLikeRepository.findByUserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> new GeneralException(MovieErrorStatus.MOVIE_LIKE_NOT_FOUND));
        movieLikeRepository.delete(movieLike);
    }
}
