package com.ceos24.springboot.favorite.service;

import com.ceos24.springboot.favorite.domain.MovieLikes;
import com.ceos24.springboot.favorite.dto.MovieLikeResponse;
import com.ceos24.springboot.favorite.repository.MovieLikesRepository;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.movie.repository.MovieRepository;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieLikeService {

    private final MovieLikesRepository movieLikesRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;


    // 영화 찜 등록
    @Transactional
    public MovieLikeResponse addMovieLike(
            Long userId,
            Long movieId
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화를 찾을 수 없습니다."
                        )
                );

        // 중복 찜 방지
        if (movieLikesRepository.existsByUserAndMovie(user, movie)) {
            throw new IllegalArgumentException(
                    "이미 찜한 영화입니다."
            );
        }

        MovieLikes movieLikes = MovieLikes.builder()
                .user(user)
                .movie(movie)
                .build();

        MovieLikes savedMovieLikes =
                movieLikesRepository.save(movieLikes);

        return MovieLikeResponse.from(savedMovieLikes);
    }


    // 내가 찜한 영화 목록
    public List<MovieLikeResponse> getMovieLikes(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        return movieLikesRepository.findAllByUser(user)
                .stream()
                .map(MovieLikeResponse::from)
                .toList();
    }


    // 영화 찜 해제
    @Transactional
    public void deleteMovieLike(
            Long userId,
            Long movieId
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화를 찾을 수 없습니다."
                        )
                );

        MovieLikes movieLikes =
                movieLikesRepository.findByUserAndMovie(user, movie)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "찜하지 않은 영화입니다."
                                )
                        );

        movieLikesRepository.delete(movieLikes);
    }
}