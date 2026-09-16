package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.MovieLike;
import com.ceos.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovieLikeService {
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MovieLikeRepository movieLikeRepository;

    @Transactional
    public boolean toggle(Long userId, Long movieId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));

        Optional<MovieLike> existingLike = movieLikeRepository.findByUser_IdAndMovie_Id(userId, movieId);
        if (existingLike.isPresent()) {
            movieLikeRepository.delete(existingLike.get());
            return false;
        }

        movieLikeRepository.save(MovieLike.builder()
                .user(user)
                .movie(movie)
                .build());
        return true;
    }
}
