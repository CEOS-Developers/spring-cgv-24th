package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.response.MovieFavoriteResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieFavorite;
import com.ceos24.cgv.domain.movie.repository.MovieFavoriteRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieFavoriteService {

    private final MovieFavoriteRepository movieFavoriteRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    // 영화 찜 등록
    @Transactional
    public Long createMovieFavorite(
            Long userId,
            Long movieId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));


        boolean existsByUserIdAndMovieId = movieFavoriteRepository.existsByUser_IdAndMovie_Id(userId, movieId);
        if (existsByUserIdAndMovieId) {
            throw new BusinessException(ErrorCode.MOVIE_ALREADY_FAVORITED);
        }

        MovieFavorite favorite = MovieFavorite.create(user, movie);

        return movieFavoriteRepository.save(favorite).getId();
    }

    // 영화 찜 해제
    @Transactional
    public void removeMovieFavorite(
            Long userId,
            Long movieId
    ) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!movieRepository.existsById(movieId)) {
            throw new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
        }

        MovieFavorite movieFavorite = movieFavoriteRepository.findByUser_IdAndMovie_Id(userId, movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_FAVORITE_NOT_FOUND));

        movieFavoriteRepository.delete(movieFavorite);
    }

    // 사용자가 찜한 영화 목록 조회
    public List<MovieFavoriteResponse> getMovieFavorites(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return movieFavoriteRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(MovieFavoriteResponse::from)
                .toList();
    }
}
