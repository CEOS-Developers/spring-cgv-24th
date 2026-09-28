package com.ceos24.cgv.domain.cinema.service;

import com.ceos24.cgv.domain.cinema.dto.response.CinemaFavoriteResponse;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.entity.CinemaFavorite;
import com.ceos24.cgv.domain.cinema.repository.CinemaFavoriteRepository;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CinemaFavoriteService {

    private final CinemaFavoriteRepository cinemaFavoriteRepository;
    private final CinemaRepository cinemaRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createCinemaFavorite(Long userId, Long cinemaId) {
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Cinema cinema = cinemaRepository.findByIdAndActiveTrue(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        if (cinemaFavoriteRepository.existsByUserEntity_IdAndCinema_Id(userId, cinemaId)) {
            throw new BusinessException(ErrorCode.CINEMA_ALREADY_FAVORITED);
        }

        CinemaFavorite favorite = CinemaFavorite.create(userEntity, cinema);
        return cinemaFavoriteRepository.save(favorite).getId();
    }

    @Transactional
    public void removeCinemaFavorite(Long userId, Long cinemaId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!cinemaRepository.existsById(cinemaId)) {
            throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
        }

        CinemaFavorite favorite = cinemaFavoriteRepository
                .findByUserEntity_IdAndCinema_Id(userId, cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_FAVORITE_NOT_FOUND));

        cinemaFavoriteRepository.delete(favorite);
    }

    public List<CinemaFavoriteResponse> getCinemaFavorites(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return cinemaFavoriteRepository.findAllByUserEntity_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(CinemaFavoriteResponse::from)
                .toList();
    }
}
