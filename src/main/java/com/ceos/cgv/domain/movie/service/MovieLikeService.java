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

@Service
@RequiredArgsConstructor
public class MovieLikeService {
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MovieLikeRepository movieLikeRepository;

    @Transactional
    public void create(Long userId, Long movieId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        if (movieLikeRepository.existsByUser_IdAndMovie_Id(userId, movieId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LIKE);
        }
        movieLikeRepository.save(new MovieLike(user, movie));
    }

    @Transactional
    public void delete(Long userId, Long movieId) {
        MovieLike movieLike = movieLikeRepository.findByUser_IdAndMovie_Id(userId, movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIKE_NOT_FOUND));
        movieLikeRepository.delete(movieLike);
    }
}
