package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.response.MovieFavoriteResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieFavorite;
import com.ceos24.cgv.domain.movie.repository.MovieFavoriteRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
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
            String username,
            Long movieId
    ) {
        UserEntity userEntity = findActiveLocalUser(username);

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));


        boolean existsByUserEntityIdAndMovieId =
                movieFavoriteRepository.existsByUserEntity_IdAndMovie_Id(userEntity.getId(), movieId);

        if (existsByUserEntityIdAndMovieId) {
            throw new BusinessException(ErrorCode.MOVIE_ALREADY_FAVORITED);
        }

        MovieFavorite favorite = MovieFavorite.create(userEntity, movie);

        return movieFavoriteRepository.save(favorite).getId();
    }

    // 영화 찜 해제
    @Transactional
    public void removeMovieFavorite(
            String username,
            Long movieId
    ) {
        UserEntity userEntity = findActiveLocalUser(username);

        if (!movieRepository.existsById(movieId)) {
            throw new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
        }

        MovieFavorite movieFavorite =
                movieFavoriteRepository.findByUserEntity_IdAndMovie_Id(userEntity.getId(), movieId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_FAVORITE_NOT_FOUND));

        movieFavoriteRepository.delete(movieFavorite);
    }

    // 사용자가 찜한 영화 목록 조회
    public List<MovieFavoriteResponse> getMovieFavorites(String username) {

        UserEntity userEntity = findActiveLocalUser(username);

        return movieFavoriteRepository
                .findAllByUserEntity_IdOrderByCreatedAtDesc(
                        userEntity.getId()
                )
                .stream()
                .map(MovieFavoriteResponse::from)
                .toList();
    }

    private UserEntity findActiveLocalUser(String username) {
        return userRepository
                .findByUsernameAndIsLockAndIsSocial(username, false, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
